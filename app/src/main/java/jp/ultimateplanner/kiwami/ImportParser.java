package jp.ultimateplanner.kiwami;

import java.util.*;
import java.util.regex.*;

/** Offline heuristics. Parsed suggestions are saved immediately by ImportActivity. */
public final class ImportParser {
    public static final class Suggestion {public String title="",note="",category="課題";public boolean fixed;public long deadline,start,end;public int estimateMin=60;public String url="";}
    private ImportParser(){}
    public static Suggestion parse(String raw){
        Suggestion s=new Suggestion();String text=raw==null?"":raw.replace('：',':').replace('／','/').replace('～','〜').trim();s.note=raw==null?"":raw.trim();s.url=findUrl(raw);
        if(text.isEmpty())return s;
        String[] lines=text.split("[\\r\\n]+");for(String line:lines){String candidate=line.trim();if(candidate.length()<2||candidate.matches("^https?://.*")||candidate.matches("^[\\d\\s/:〜\\-.]+$"))continue;
            if(candidate.length()>80)continue;
            if(candidate.matches("^(締切|期限|提出期限|提出日|開始日時|終了日時|日時|日付)\\s*[:：].*"))continue;
            candidate=candidate.replaceFirst("^(件名|タイトル|内容|予定名|課題名)\\s*[:：]\\s*","");
            if(!candidate.isEmpty()){s.title=candidate.length()>48?candidate.substring(0,48):candidate;break;}}
        if(s.title.isEmpty())s.title="取り込んだ予定";
        if(contains(text,"バイト","シフト","勤務","出勤"))s.category="アルバイト";
        else if(contains(text,"面接","エントリー","ES","選考","説明会","インターン","企業"))s.category="就活";
        else if(contains(text,"授業","講義","時間割","ゼミ"))s.category="授業";
        else if(contains(text,"課題","提出","レポート","小テスト","試験"))s.category="課題";
        else s.category="私用";
        long date=parseDate(text);
        Matcher explicitDeadline=Pattern.compile("(?:締切|期限|提出日|提出期限|〆切)\\s*[:：]?\\s*([^\\n\\r]{1,50})").matcher(text);
        if(explicitDeadline.find()){
            long chosen=parseDate(explicitDeadline.group(1));
            if(chosen>0)date=chosen;
        }
        List<Integer> hm=times(text);
        Matcher estimate=Pattern.compile("(?<![0-9])(\\d{1,3})\\s*(?:分|minutes)(?:\\s*(?:程度|所要|必要))?").matcher(text);
        if(estimate.find())s.estimateMin=Math.max(15,Integer.parseInt(estimate.group(1)));
        else if(text.matches("(?s).*(?:所要時間|作業時間|見積).*?[0-9]{1,2}時間.*")) {
            Matcher duration=Pattern.compile("([0-9]{1,2})時間").matcher(text);
            if(duration.find())s.estimateMin=Integer.parseInt(duration.group(1))*60;
        }
        boolean due=contains(text,"締切","期限","提出期限","提出日","〆切","〆","必着");
        // If both ends of a range are given, treat as a fixed appointment unless a clear due-date label exists.
        boolean interval=Pattern.compile("(?:(?:\\d{1,2}):(?:\\d{2}))\\s*(?:〜|～|[-–])\\s*(?:(?:\\d{1,2}):(?:\\d{2}))").matcher(text).find();
        s.fixed=!due&&(interval||contains(text,"面接","説明会","シフト","授業","講義","予約","集合"));
        if(date==0){s.fixed=false;s.deadline=0;return s;}
        if(s.fixed){int h=hm.size()>0?hm.get(0)/60:13,m=hm.size()>0?hm.get(0)%60:0;
            s.start=TimeUtil.at(date,h,m);int end=hm.size()>1?hm.get(1):(h*60+m+60);
            s.end=TimeUtil.at(date,end/60%24,end%60);
            if(s.end<=s.start)s.end=hm.size()>1?TimeUtil.plusDay(s.end,1):s.start+3600000L;
        } else {int h=hm.size()>0?hm.get(0)/60:23,m=hm.size()>0?hm.get(0)%60:59;s.deadline=TimeUtil.at(date,h,m);}
        return s;
    }
    /** A dated line can represent a separate item in a list-type screenshot. */
    public static List<Suggestion> parseMany(String raw) {
        List<Suggestion> out=new ArrayList<>();
        if(raw==null||raw.trim().isEmpty())return out;
        String[] lines=raw.split("[\\r\\n]+");
        ArrayList<String> dated=new ArrayList<>();
        String dateRegex="(?s).*(?:20[0-9]{2}[/.-][0-9]{1,2}[/.-][0-9]{1,2}|[0-9]{1,2}[/月.-][0-9]{1,2}|(?:今日|明日|明後日|来週[月火水木金土日]曜?)).*";
        for(int i=0;i<lines.length;i++) {
            String t=lines[i].trim();
            if(!t.matches(dateRegex)||t.length()>120)continue;
            if((t.matches("^(締切|期限|提出期限|提出日|日付|日時)\\s*[:：].*")||t.matches("^[0-9/年月日: \\t()（）-]+$"))
                && i+1<lines.length) {
                String next=lines[i+1].trim();
                if(next.length()>1&&next.length()<65&&!next.matches(dateRegex)) t=next+"\n"+t;
            }
            dated.add(t);
        }
        // Only split when entries actually occur on multiple independently dated lines.
        if(dated.size()>=2&&dated.size()<=15){
            String commonUrl=findUrl(raw);
            for(String line:dated){Suggestion s=parse(line);if(s.url.isEmpty())s.url=commonUrl;if(!s.title.isEmpty())out.add(s);}
            return out;
        }
        out.add(parse(raw));return out;
    }
    /** Pure Java URL extraction shared by OCR and imported screenshots. */
    public static String findUrl(String text){
        if(text==null)return "";
        Matcher m=Pattern.compile("(?i)https?://[^\\s<>()]+").matcher(text);
        while(m.find()){
            String value=m.group().replaceAll("[.,;:!?）)、。]+$","");
            try{java.net.URI uri=new java.net.URI(value);
                if(uri.getHost()!=null && ("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme())))return value;
            }catch(Exception ignored){}
        }
        return "";
    }
    private static boolean contains(String text,String... words){for(String w:words)if(text.contains(w))return true;return false;}
    private static List<Integer> times(String s){List<Integer> out=new ArrayList<>();Matcher m=Pattern.compile("(?<![0-9])([01]?\\d|2[0-3])[:時]([0-5]\\d)(?![0-9])").matcher(s);
        while(m.find()&&out.size()<2)out.add(Integer.parseInt(m.group(1))*60+Integer.parseInt(m.group(2)));
        if(out.isEmpty()){Matcher h=Pattern.compile("(?<![0-9])([01]?\\d|2[0-3])時(?![0-9])").matcher(s);while(h.find()&&out.size()<2)out.add(Integer.parseInt(h.group(1))*60);}
        return out;}
    private static long parseDate(String s){
        Matcher iso=Pattern.compile("(?<![0-9])(20\\d{2})[/-年.]\\s*(1[0-2]|0?[1-9])[/-月.]\\s*(3[01]|[12]\\d|0?[1-9])(?:日)?").matcher(s);
        if(iso.find())return date(Integer.parseInt(iso.group(1)),Integer.parseInt(iso.group(2)),Integer.parseInt(iso.group(3)));
        Matcher md=Pattern.compile("(?<![0-9])(0?[1-9]|1[0-2])[/月.-]\\s*(0?[1-9]|[12][0-9]|3[01])(?:日)?(?![0-9])").matcher(s);
        if(md.find()){int month=Integer.parseInt(md.group(1)),day=Integer.parseInt(md.group(2));Calendar c=Calendar.getInstance();int y=c.get(Calendar.YEAR);long d=date(y,month,day);
            if(d<TimeUtil.day(System.currentTimeMillis())-30*86400000L)d=date(y+1,month,day);return d;}
        long now=System.currentTimeMillis();
        Matcher daysAfter=Pattern.compile("(?<![0-9])([1-9]|[12][0-9]|3[01])日後").matcher(s);
        if(daysAfter.find())return TimeUtil.plusDay(TimeUtil.day(now),Integer.parseInt(daysAfter.group(1)));
        if(s.contains("明後日"))return TimeUtil.plusDay(TimeUtil.day(now),2);if(s.contains("明日"))return TimeUtil.plusDay(TimeUtil.day(now),1);if(s.contains("今日"))return TimeUtil.day(now);
        Matcher week=Pattern.compile("(来週|今週)?\\s*(?:の)?([月火水木金土日])(?:曜日|曜)").matcher(s);
        if(week.find()){String day=week.group(2);int[] indexes={Calendar.MONDAY,Calendar.TUESDAY,Calendar.WEDNESDAY,Calendar.THURSDAY,Calendar.FRIDAY,Calendar.SATURDAY,Calendar.SUNDAY};int wanted=indexes["月火水木金土日".indexOf(day)];Calendar c=Calendar.getInstance();int delta=(wanted-c.get(Calendar.DAY_OF_WEEK)+7)%7;if("来週".equals(week.group(1))){long nextWeek=TimeUtil.plusDay(TimeUtil.weekStart(now),7);return TimeUtil.plusDay(nextWeek,"月火水木金土日".indexOf(day));}return TimeUtil.plusDay(TimeUtil.day(now),delta);}
        return 0;
    }
    private static long date(int year,int month,int day){Calendar c=Calendar.getInstance();c.setLenient(false);c.set(year,month-1,day,0,0,0);c.set(Calendar.MILLISECOND,0);try{return c.getTimeInMillis();}catch(IllegalArgumentException e){return 0;}}
}