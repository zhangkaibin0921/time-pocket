package cn.timepocket.app;

import java.time.*;
import java.time.temporal.ChronoUnit;

public final class UsageBucketsTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        LocalDate first=LocalDate.of(2026,10,30);
        var week=UsageBuckets.days(first,first.plusDays(6));
        check(week.size()==7,"seven day columns");
        check(week.get(5).first().equals(LocalDate.of(2026,11,4)),"tap sixth column selects Nov 4 across month boundary");
        check(week.stream().allMatch(d->d.first().equals(d.last())),"each weekly column is exactly one day");
        for(int days:new int[]{1,7,30,31,90,365}){
            var buckets=UsageBuckets.days(first,first.plusDays(days-1));
            check(buckets.size()==Math.min(days,30),"maximum 30 columns");
            check(buckets.get(0).first().equals(first),"first endpoint preserved");
            check(buckets.get(buckets.size()-1).last().equals(first.plusDays(days-1)),"last endpoint preserved");
            long total=0;LocalDate next=first;
            for(var b:buckets){check(b.first().equals(next)&&!b.last().isBefore(b.first()),"no gaps, overlaps or empty buckets");total+=ChronoUnit.DAYS.between(b.first(),b.last())+1;next=b.last().plusDays(1);}
            check(total==days,"each calendar day covered exactly once");
        }
        var leap=UsageBuckets.days(LocalDate.of(2024,2,28),LocalDate.of(2024,3,1));
        check(leap.get(1).first().equals(LocalDate.of(2024,2,29)),"leap day selectable");
        var dst=UsageBuckets.days(LocalDate.of(2026,3,8),LocalDate.of(2026,3,8)).get(0);ZoneId zone=ZoneId.of("America/New_York");
        check(Duration.between(dst.first().atStartOfDay(zone),dst.last().plusDays(1).atStartOfDay(zone)).toHours()==23,"local day bounds respect daylight saving");
        boolean rejected=false;try{UsageBuckets.days(first,first.minusDays(1));}catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"invalid range rejected");
        System.out.println("PASS: "+checks+" chart date-bucket assertions");
    }
}
