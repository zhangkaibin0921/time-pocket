package cn.timepocket.app;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.zone.ZoneOffsetTransition;
import java.util.ArrayList;
import java.util.List;

/** Local time ranges represented by the chart columns. */
final class UsageBuckets {
    record Days(LocalDate first,LocalDate last) {}
    record Hour(long start,long end) {}

    static Hour hour(LocalDate day,int index,ZoneId zone,long upTo){
        long start=hourBoundary(day.atStartOfDay().plusHours(index),zone);
        long end=hourBoundary(day.atStartOfDay().plusHours(index+1),zone);
        return new Hour(start,Math.max(start,Math.min(end,upTo)));
    }
    private static long hourBoundary(LocalDateTime time,ZoneId zone){
        ZoneOffsetTransition change=zone.getRules().getTransition(time);
        return (change!=null&&change.isGap()?change.getInstant():time.atZone(zone).toInstant()).toEpochMilli();
    }

    static List<Days> days(LocalDate first,LocalDate last){
        long count=ChronoUnit.DAYS.between(first,last)+1;
        if(count<1)throw new IllegalArgumentException("End before start");
        int columns=(int)Math.min(30,count);List<Days> result=new ArrayList<>();
        for(int i=0;i<columns;i++)result.add(new Days(first.plusDays(count*i/columns),first.plusDays(count*(i+1)/columns-1)));
        return result;
    }
    private UsageBuckets() {}
}
