package jp.ultimateplanner.kiwami;
import java.util.*;

public final class Priority {
    private Priority(){}
    public static double score(Entry e,long now){
        if(e.done())return -10000;
        double hours=e.deadlineMs>0?(e.deadlineMs-now)/3600000.0:9999;
        double urgency= e.deadlineMs==0?8:hours<=0?105:hours<12?95:hours<24?80:hours<72?64:hours<168?42:hours<336?25:12;
        return urgency+e.difficulty*3.5+Math.min(e.remainingMin()/60.0,10)*1.5+Math.max(0,30-e.progress)*.10;
    }
    public static List<Entry> tasks(List<Entry> all,long now){List<Entry> result=new ArrayList<>();for(Entry e:all)if(!e.fixed)result.add(e);result.sort((a,b)->Double.compare(score(b,now),score(a,now)));return result;}
    public static String label(Entry e,long now){double p=score(e,now);return p>=80?"最優先":p>=55?"優先":p>=30?"通常":"余裕あり";}
}
