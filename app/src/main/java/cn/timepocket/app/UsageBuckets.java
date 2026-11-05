package cn.timepocket.app;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Inclusive calendar days represented by each daily chart column. */
final class UsageBuckets {
    record Days(LocalDate first,LocalDate last) {}
    static List<Days> days(LocalDate first,LocalDate last){
        long count=ChronoUnit.DAYS.between(first,last)+1;
        if(count<1)throw new IllegalArgumentException("End before start");
        int columns=(int)Math.min(30,count);List<Days> result=new ArrayList<>();
        for(int i=0;i<columns;i++)result.add(new Days(first.plusDays(count*i/columns),first.plusDays(count*(i+1)/columns-1)));
        return result;
    }
    private UsageBuckets() {}
}
