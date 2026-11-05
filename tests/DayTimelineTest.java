package cn.timepocket.app;
import java.util.*;

public class DayTimelineTest {
    static int checks;
    static void eq(Object actual,Object expected,String label){checks++;if(!actual.equals(expected))throw new AssertionError(label+": "+actual+" != "+expected);}
    static DayTimeline.Span span(long a,long b){return new DayTimeline.Span(a,b);}
    public static void main(String[] args){
        var overlap=List.of(span(-20,30),span(10,50),span(50,70),span(90,120));
        eq(DayTimeline.union(overlap,0,100),List.of(span(0,70),span(90,100)),"clip and merge touching/overlapping intervals");
        eq(DayTimeline.duration(overlap,0,100),80L,"simultaneous apps counted once");
        eq(DayTimeline.blanks(overlap,0,100),List.of(span(70,90)),"blank excludes both apps and notes");
        eq(DayTimeline.blanks(List.of(),0,47),List.of(span(0,47)),"today stops at now");
        eq(DayTimeline.blanks(List.of(span(-5,120)),0,100),List.of(),"cross-day activity fills day");
        eq(DayTimeline.blanks(List.of(span(10,20),span(30,40)),0,50),List.of(span(0,10),span(20,30),span(40,50)),"all three gaps preserved");
        eq(DayTimeline.blanks(List.of(span(100,120)),0,100),List.of(span(0,100)),"exclusive midnight boundary");
        eq(DayTimeline.union(List.of(span(80,20),span(4,4)),0,100),List.of(),"empty and invalid ranges ignored");
        eq(DayTimeline.blanks(overlap,0,0),List.of(),"no elapsed time at midnight");
        var apps=DayTimeline.mergeApps(List.of(new DayTimeline.Item("a","A",-10,30),new DayTimeline.Item("a","A",20,40),new DayTimeline.Item("a","A",50,60),new DayTimeline.Item("b","B",25,45)),0,55);
        eq(apps.size(),3,"merge only same-app continuous pieces");
        eq(apps.get(0),new DayTimeline.Item("a","A",0,40),"clip merged app");
        eq(apps.get(1),new DayTimeline.Item("b","B",25,45),"parallel app remains visible");
        eq(apps.get(2).end(),55L,"clip at current time");
        long h=3600000L;
        eq(DayTimeline.sleep(List.of(span(22*h,23*h),span(32*h,33*h)),18*h,24*h,30*h,36*h),span(23*h,32*h),"last evening use to first morning use");
        eq(DayTimeline.sleep(List.of(span(23*h,25*h),span(32*h,33*h)),18*h,24*h,30*h,36*h),span(25*h,32*h),"bedtime after midnight");
        eq(DayTimeline.sleep(List.of(span(22*h,23*h),span(24*h,25*h),span(32*h,33*h)),18*h,24*h,30*h,36*h),span(25*h,32*h),"prefer main overnight break over short midnight pause");
        eq(DayTimeline.sleep(List.of(span(22*h,23*h),span(22*h,24*h),span(32*h,33*h)),18*h,24*h,30*h,36*h),span(24*h,32*h),"parallel apps end before sleep begins");
        eq(DayTimeline.sleep(List.of(span(22*h,23*h)),18*h,24*h,30*h,36*h)==null,true,"no wake event means no invented sleep end");
        eq(DayTimeline.sleep(List.of(span(32*h,33*h)),18*h,24*h,30*h,36*h)==null,true,"no bedtime event means no invented start");
        eq(DayTimeline.sleep(List.of(span(31*h,32*h),span(34*h,35*h)),18*h,24*h,30*h,36*h)==null,true,"daytime idle is not overnight sleep");
        long m=60000L;
        var night=new ArrayList<>(List.of(new DayTimeline.Item("a","A",22*h,23*h),new DayTimeline.Item("a","A",32*h,33*h)));
        night.add(new DayTimeline.Item("b","B",27*h,27*h+30000));
        eq(DayTimeline.sleepApps(night,Set.of(),18*h,24*h,30*h,36*h),span(23*h,32*h),"hidden midnight glance no longer splits sleep");
        night.add(new DayTimeline.Item("b","B",31*h,31*h+59999));
        night.add(new DayTimeline.Item("b","B",23*h+10*m,23*h+10*m+30000));
        eq(DayTimeline.sleepApps(night,Set.of(),18*h,24*h,30*h,36*h),span(23*h,32*h),"hidden bedtime and morning glances do not move sleep edges");
        night.add(new DayTimeline.Item("home","Home",26*h,28*h));
        eq(DayTimeline.sleepApps(night,Set.of("home"),18*h,24*h,30*h,36*h),span(23*h,32*h),"hidden desktop does not split inferred sleep");
        night.add(new DayTimeline.Item("b","B",25*h,25*h+m));
        eq(DayTimeline.sleepApps(night,Set.of("home"),18*h,24*h,30*h,36*h),span(25*h+m,32*h),"exact minute of night use still bounds main sleep");
        eq(DayTimeline.sleepApps(List.of(new DayTimeline.Item("a","A",22*h,23*h),new DayTimeline.Item("a","A",32*h,32*h+59999)),Set.of(),18*h,24*h,30*h,36*h)==null,true,"short wake alone cannot invent a sleep end");
        eq(DayTimeline.sleepApps(List.of(new DayTimeline.Item("a","A",23*h,23*h+59999),new DayTimeline.Item("a","A",32*h,33*h)),Set.of(),18*h,24*h,30*h,36*h)==null,true,"short bedtime alone cannot invent a sleep start");
        eq(DayTimeline.sleepApps(List.of(),Set.of(),18*h,24*h,30*h,36*h)==null,true,"empty filtered night has no inferred sleep");
        var midnightPieces=List.of(new DayTimeline.Item("a","A",22*h,23*h),new DayTimeline.Item("b","B",24*h-40000,24*h+40000),new DayTimeline.Item("a","A",32*h,33*h));
        eq(DayTimeline.sleepApps(midnightPieces,Set.of(),18*h,24*h,30*h,36*h),span(23*h,32*h),"midnight clipping matches short fragments hidden on both day pages");
        var touchingShorts=List.of(new DayTimeline.Item("a","A",22*h,23*h),new DayTimeline.Item("b","B",27*h,27*h+40000),new DayTimeline.Item("b","B",27*h+40000,27*h+80000),new DayTimeline.Item("a","A",32*h,33*h));
        eq(DayTimeline.sleepApps(touchingShorts,Set.of(),18*h,24*h,30*h,36*h),span(23*h,32*h),"sleep also filters before joining short fragments");
        eq(DayTimeline.sleepApps(midnightPieces,Set.of(),18*h,24*h,30*h,32*h+30000)==null,true,"today waits for at least a minute of observed morning use");
        eq(DayTimeline.sleepApps(midnightPieces,Set.of(),18*h,24*h,30*h,32*h+m),span(23*h,32*h),"today uses observed wake edge once minute threshold is met");
        var fragmented=List.of(new DayTimeline.Item("a","A",0,8*m),new DayTimeline.Item("home","桌面",8*m,10*m),new DayTimeline.Item("a","A",10*m,20*m));
        var joined=DayTimeline.groupApps(fragmented,Set.of("home"),List.of(),0,30*m);
        eq(joined.size(),1,"hide home before joining neighboring app segments");
        eq(joined.get(0).start(),0L,"merged card begins at first use");eq(joined.get(0).end(),20*m,"merged card ends at last use");
        eq(joined.get(0).used(),18*m,"desktop and short break not added to actual duration");
        eq(joined.get(0).breaks(),List.of(span(8*m,10*m)),"bridged break remains available for manual entry");
        eq(joined.get(0).segments().size(),2,"detail retains both original segments");
        eq(DayTimeline.groupApps(fragmented,Set.of(),List.of(),0,30*m).size(),3,"other visible app stops merging");
        eq(DayTimeline.groupApps(fragmented,Set.of("home"),List.of(span(9*m,10*m)),0,30*m).size(),2,"manual record in gap stops merging");
        var threshold=List.of(new DayTimeline.Item("a","A",0,m),new DayTimeline.Item("a","A",6*m,7*m));
        eq(DayTimeline.groupApps(threshold,Set.of(),List.of(),0,10*m).size(),1,"exact five-minute gap joins");
        eq(DayTimeline.groupApps(List.of(threshold.get(0),new DayTimeline.Item("a","A",6*m+1,7*m+1)),Set.of(),List.of(),0,10*m).size(),2,"longer gap stays separate");
        eq(DayTimeline.groupApps(List.of(new DayTimeline.Item("b","B",0,25*m),new DayTimeline.Item("a","A",m,8*m),new DayTimeline.Item("a","A",10*m,20*m)),Set.of(),List.of(),0,30*m).size(),3,"earlier parallel app occupying gap blocks join");
        eq(DayTimeline.groupApps(fragmented,Set.of("home"),List.of(),5*m,15*m).get(0).used(),8*m,"day bounds clip actual usage before joining");
        eq(DayTimeline.groupApps(List.of(new DayTimeline.Item("home","桌面",0,10*m)),Set.of("home"),List.of(),0,20*m).size(),0,"home-only day has no visible application cards");
        eq(DayTimeline.groupApps(List.of(new DayTimeline.Item("a","A",0,m),new DayTimeline.Item("a","A",2*m,3*m),new DayTimeline.Item("a","A",4*m,5*m)),Set.of(),List.of(),0,10*m).get(0).used(),3*m,"chained joins never count intervening breaks");
        eq(fragmented.size(),3,"presentation does not mutate raw records");
        eq(DayTimeline.groupApps(List.of(new DayTimeline.Item("b","B",0,30*m),new DayTimeline.Item("c","C",m,3*m),new DayTimeline.Item("a","A",4*m,8*m),new DayTimeline.Item("a","A",10*m,12*m)),Set.of(),List.of(),0,40*m).size(),4,"long-lived earlier app still blocks after intervening packages");
        eq(DayTimeline.groupApps(List.of(new DayTimeline.Item("a","A",0,m),new DayTimeline.Item("a","A",2*m,3*m),new DayTimeline.Item("b","B",4*m,5*m),new DayTimeline.Item("a","A",6*m,7*m),new DayTimeline.Item("a","A",8*m,9*m)),Set.of(),List.of(),0,10*m).size(),3,"expired competing app does not block later join");
        var shortSwitch=List.of(new DayTimeline.Item("a","A",0,2*m),new DayTimeline.Item("b","B",2*m,3*m-1),new DayTimeline.Item("a","A",3*m,5*m));
        var filtered=DayTimeline.groupApps(shortSwitch,Set.of(),List.of(),0,10*m);
        eq(filtered.size(),1,"remove sub-minute competing app before joining same app");
        eq(filtered.get(0).used(),4*m,"discarded app and bridged gap do not inflate usage");
        eq(filtered.get(0).breaks(),List.of(span(2*m,3*m)),"discarded interval can be filled manually");
        eq(shortSwitch.size(),3,"short filtering leaves source records intact");
        eq(DayTimeline.groupApps(List.of(new DayTimeline.Item("a","A",0,m-1)),Set.of(),List.of(),0,10*m).size(),0,"59999 milliseconds hidden");
        eq(DayTimeline.groupApps(List.of(new DayTimeline.Item("a","A",0,m)),Set.of(),List.of(),0,10*m).size(),1,"exact minute retained");
        eq(DayTimeline.groupApps(List.of(new DayTimeline.Item("a","A",0,40000),new DayTimeline.Item("a","A",40000,80000)),Set.of(),List.of(),0,10*m).size(),0,"short same-app fragments removed before continuous union");
        eq(DayTimeline.groupApps(List.of(new DayTimeline.Item("a","A",-m,30000)),Set.of(),List.of(),0,10*m).size(),0,"threshold applies to visible day portion");
        eq(DayTimeline.groupApps(shortSwitch,Set.of(),List.of(span(2*m,3*m)),0,10*m).size(),2,"filtering never removes manual or sleep barriers");
        eq(DayTimeline.groupApps(List.of(shortSwitch.get(0),new DayTimeline.Item("b","B",2*m,3*m),shortSwitch.get(2)),Set.of(),List.of(),0,10*m).size(),3,"minute-long competing app still prevents join");
        eq(DayTimeline.groupApps(List.of(shortSwitch.get(0),shortSwitch.get(1),new DayTimeline.Item("a","A",8*m,10*m)),Set.of(),List.of(),0,10*m).size(),2,"deleted fragments do not shorten five-minute gap limit");
        Random random=new Random(73);
        for(int trial=0;trial<30;trial++){
            List<DayTimeline.Item> input=new ArrayList<>();String[] packages={"a","b","c","home"};
            for(int i=0;i<50;i++){long a=random.nextInt(90)*m;String pkg=packages[random.nextInt(packages.length)];input.add(new DayTimeline.Item(pkg,pkg,a,a+(1+random.nextInt(5))*m));}
            List<DayTimeline.Span> barriers=List.of(span(20*m,22*m),span(60*m,63*m));
            List<DayTimeline.Group> grouped=DayTimeline.groupApps(input,Set.of("home"),barriers,5*m,85*m);
            for(String pkg:List.of("a","b","c")){
                List<DayTimeline.Span> original=new ArrayList<>();for(var item:input)if(item.pkg().equals(pkg))original.add(span(item.start(),item.end()));
                long used=grouped.stream().filter(g->g.pkg().equals(pkg)).mapToLong(DayTimeline.Group::used).sum();
                eq(used,DayTimeline.duration(original,5*m,85*m),"randomized grouping retains exact clipped duration");
            }
            boolean valid=true;for(var group:grouped)for(var gap:group.breaks()){
                valid&=gap.end()-gap.start()<=DayTimeline.SHORT_BREAK;
                for(var b:barriers)valid&=b.end()<=gap.start()||b.start()>=gap.end();
                for(var other:input)if(!other.pkg().equals("home")&&!other.pkg().equals(group.pkg()))valid&=other.end()<=gap.start()||other.start()>=gap.end();
            }eq(valid,true,"randomized joins respect activities and competing apps");
        }
        System.out.println("PASS: "+checks+" daily timeline assertions");
    }
}
