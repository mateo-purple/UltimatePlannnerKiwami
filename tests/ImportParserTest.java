package jp.ultimateplanner.kiwami;

import java.util.List;

public final class ImportParserTest {
    private static void check(boolean value,String name) {
        if(!value)throw new AssertionError(name);
    }
    public static void main(String[] args) {
        ImportParser.Suggestion interview=ImportParser.parse("企業面接 2026/11/25 14:00〜15:00");
        check(interview.fixed,"interview should be fixed");
        check("就活".equals(interview.category),"interview category");
        check(interview.end>interview.start,"interview duration");
        ImportParser.Suggestion report=ImportParser.parse("レポートの提出期限: 2026/11/28 23:59");
        check(!report.fixed,"assignment is a task");
        check(report.deadline>0,"assignment deadline");
        List<ImportParser.Suggestion> two=ImportParser.parseMany("企業面接 2026/11/25 14:00〜15:00\nバイト 2026/11/26 18:00〜22:00");
        check(two.size()==2,"multiple dated rows");
        check(TimeUtil.weekStart(two.get(0).start)<=two.get(0).start,"valid week date");
        System.out.println("ImportParser checks passed");
    }
}