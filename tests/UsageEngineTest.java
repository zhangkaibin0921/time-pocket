package cn.timepocket.app;
import java.util.*;

public class UsageEngineTest {
    static int checks;
    static UsageEngine.Event e(long at,String pkg,String activity,int type) {return new UsageEngine.Event(at,pkg,activity,type);}
    static void eq(long actual,long expected,String label) {checks++;if(actual!=expected)throw new AssertionError(label+": "+actual+" != "+expected);}
    static long sum(UsageEngine.Result r) {return r.segments().stream().mapToLong(s->s.end()-s.start()).sum();}
    static UsageEngine.Result run(UsageEngine.State s,long from,long to,UsageEngine.Event... events) {return UsageEngine.reduce(s,new ArrayList<>(Arrays.asList(events)),from,to);}
    public static void main(String[] args) {
        eq(sum(run(new UsageEngine.State(),0,100,e(10,"a","1",1),e(30,"a","1",2))),20,"normal session");
        eq(sum(run(new UsageEngine.State(),0,100,e(10,"a","1",1),e(15,"a","2",1),e(20,"a","1",2),e(40,"a","2",2))),30,"overlapping activities counted once per package");
        UsageEngine.State s=new UsageEngine.State();
        eq(sum(run(s,0,50,e(10,"a","1",1))),40,"ongoing session flushed");
        eq(sum(run(s,50,100,e(70,"a","1",2))),20,"next sync does not duplicate prior period");
        eq(sum(run(s,100,110)),0,"repeated refresh no new activity");
        eq(sum(run(new UsageEngine.State(),0,100,e(10,"a","1",1),e(25,null,"",16),e(70,"a","1",2))),15,"screen off closes foreground");
        eq(sum(run(new UsageEngine.State(),0,100,e(10,"a","1",1),e(25,null,"",15),e(70,"a","1",2))),60,"screen interactive must not close foreground");
        eq(sum(run(new UsageEngine.State(),0,100,e(10,"a","1",1),e(25,null,"",17))),15,"lock closes foreground");
        eq(sum(run(new UsageEngine.State(),0,100,e(30,"a","1",2))),0,"missing resume is not invented");
        eq(sum(run(new UsageEngine.State(),0,100,e(10,"a","1",1),e(30,null,"",26))),0,"shutdown persistence marker not treated as precise end");
        eq(sum(run(new UsageEngine.State(),0,100,e(10,"a","1",1),e(30,null,"",27),e(60,"a","1",1),e(80,"a","1",2))),20,"boot clears stale state");
        eq(sum(run(new UsageEngine.State(),0,100,e(10,"a","1",1),e(10,"a","1",1),e(30,"a","1",2))),20,"duplicate resume");
        eq(sum(run(new UsageEngine.State(),0,100,e(10,"a","1",1),e(20,"b","1",1),e(40,"a","1",2),e(60,"b","1",2))),70,"split screen per-app foreground semantics");
        eq(UsageEngine.overlap(10,100,30,40),10,"custom interval clipping");
        eq(UsageEngine.overlap(10,30,30,60),0,"half-open intervals");
        eq(UsageEngine.overlap(23*3600000L,25*3600000L,24*3600000L,48*3600000L),3600000L,"cross-midnight split");
        eq(sum(run(new UsageEngine.State(),0,UsageEngine.MAX_SESSION+2,e(1,"a","1",1))),0,"unbounded session rejected");
        s=new UsageEngine.State();
        eq(sum(run(s,0,30,e(10,"a","1",1),e(30,"a","1",2))),20,"boundary event deferred");
        eq(sum(run(s,30,50,e(30,"a","1",2))),0,"deferred pause does not add phantom time");
        eq(sum(run(new UsageEngine.State(),0,100,e(10,"a","1",1),e(20,"a","1",2),e(60,"a","2",1),e(80,"a","2",2))),30,"same package across reinstall sessions accumulates");
        s=new UsageEngine.State();run(s,0,3600000,e(0,"a","1",1));
        for(int hour=1;hour<12;hour++) run(s,hour*3600000L,(hour+1)*3600000L);
        eq(sum(run(s,12*3600000L,13*3600000L)),0,"missing close eventually expires across incremental syncs");
        eq(s.activities.size(),0,"expired activity state cleared");
        List<UsageEngine.Event> late=new ArrayList<>();late.add(e(10,"late","A",1));
        eq(sum(UsageEngine.confirmed(late,0,100)),0,"unclosed tail never invents duration");
        late.add(e(40,"late","A",2));
        eq(sum(UsageEngine.confirmed(late,0,100)),30,"late pause reconstructs complete session");
        eq(sum(UsageEngine.confirmed(late,0,200)),30,"replay does not extend closed session");
        eq(sum(UsageEngine.confirmed(new ArrayList<>(List.of(e(0,"long","A",1),e(13*3600000L,"long","A",2))),0,14*3600000L)),13*3600000L,"explicit long session not discarded by arbitrary cap");
        System.out.println("PASS: "+checks+" usage accounting assertions");
    }
}
