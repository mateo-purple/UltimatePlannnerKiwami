package jp.ultimateplanner.kiwami;

public class Entry {
    public long id;
    public String syncId = java.util.UUID.randomUUID().toString();
    public long updatedMs = System.currentTimeMillis();
    public boolean deleted = false;
    public String title = "";
    public String note = "";
    public String category = "課題";
    public String source = "手入力";
    public boolean fixed = false;
    public boolean weekly = false;
    public long startMs = 0;
    public long endMs = 0;
    public long deadlineMs = 0;
    public int estimateMin = 60;
    public int progress = 0;
    public int difficulty = 3;
    public long createdMs = System.currentTimeMillis();

    public int remainingMin() {
        return Math.max(0, (int)Math.ceil(estimateMin * (100 - progress) / 100.0));
    }
    public boolean isTask() { return !fixed; }
    public boolean done() { return !fixed && progress >= 100; }
}
