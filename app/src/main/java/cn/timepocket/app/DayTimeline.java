package cn.timepocket.app;

import java.util.*;

/** Wall-clock coverage: simultaneous apps and manual activities never multiply a day. */
final class DayTimeline {
    record Span(long start,long end) {}
    record Item(String pkg,String label,long start,long end) {}
    static final long SHORT_BREAK=5*60*1000L;
    static final long MIN_APP_DURATION=60*1000L;
    record Group(String pkg,String label,List<Span> segments){
        Group {segments=List.copyOf(segments);}
        long start(){return segments.get(0).start;}
        long end(){return segments.get(segments.size()-1).end;}
        long used(){long result=0;for(Span s:segments)result+=s.end-s.start;return result;}
        List<Span> breaks(){return blanks(segments,start(),end());}
    }
    /** Shared by the day cards and overnight inference, before any merging. */
    static List<Item> visibleApps(List<Item> input,Set<String> hidden,long begin,long end){
        // Filter each day's individual fragments BEFORE any same-app merging.
        List<Item> visible=new ArrayList<>();for(Item item:input){
            long a=Math.max(begin,item.start),b=Math.min(end,item.end);
            if(!hidden.contains(item.pkg)&&b>a&&b-a>=MIN_APP_DURATION)visible.add(new Item(item.pkg,item.label,a,b));
        }
        return visible;
    }
    /** Presentation only: retain actual spans so bridged breaks never inflate usage. */
    static List<Group> groupApps(List<Item> input,Set<String> hidden,List<Span> barriers,long begin,long end){
        List<Item> visible=mergeApps(visibleApps(input,hidden,begin,end),begin,end);List<Group> result=new ArrayList<>();
        List<Span> blocked=union(barriers,begin,end),spans=new ArrayList<>();
        RunningEnds previous=new RunningEnds();String pkg=null,label=null;long lastEnd=0;
        for(Item item:visible){
            boolean join=item.pkg.equals(pkg)&&item.start>=lastEnd&&item.start-lastEnd<=SHORT_BREAK
                    &&!intersects(blocked,lastEnd,item.start)&&previous.otherEnd(item.pkg)<=lastEnd;
            if(!join){
                if(pkg!=null)result.add(new Group(pkg,label,spans));
                spans=new ArrayList<>();pkg=item.pkg;label=item.label;
            }
            spans.add(new Span(item.start,item.end));lastEnd=item.end;previous.add(item.pkg,item.end);
        }
        if(pkg!=null)result.add(new Group(pkg,label,spans));return result;
    }
    private static boolean intersects(List<Span> sorted,long start,long end){
        int low=0,high=sorted.size();
        while(low<high){int mid=(low+high)>>>1;if(sorted.get(mid).end<=start)low=mid+1;else high=mid;}
        return low<sorted.size()&&sorted.get(low).start<end;
    }
    /** Largest observed end for each package; overlapping earlier apps still block a join. */
    private static final class RunningEnds {
        final Map<String,Long> packages=new HashMap<>();
        final TreeMap<Long,Set<String>> ends=new TreeMap<>();
        void add(String pkg,long end){
            Long old=packages.get(pkg);if(old!=null&&old>=end)return;
            if(old!=null){Set<String> set=ends.get(old);set.remove(pkg);if(set.isEmpty())ends.remove(old);}
            packages.put(pkg,end);ends.computeIfAbsent(end,k->new HashSet<>()).add(pkg);
        }
        long otherEnd(String pkg){
            Map.Entry<Long,Set<String>> last=ends.lastEntry();if(last==null)return Long.MIN_VALUE;
            if(last.getValue().size()==1&&last.getValue().contains(pkg))last=ends.lowerEntry(last.getKey());
            return last==null?Long.MIN_VALUE:last.getKey();
        }
    }
    /** Match each day's filtering, including fragments clipped at midnight. */
    static Span sleepApps(List<Item> input,Set<String> hidden,long evening,long midnight,long lastBedtime,long until){
        List<Span> phone=new ArrayList<>();
        for(Item item:visibleApps(input,hidden,evening,Math.min(midnight,until)))phone.add(new Span(item.start,item.end));
        for(Item item:visibleApps(input,hidden,midnight,until))phone.add(new Span(item.start,item.end));
        return sleep(phone,evening,midnight,lastBedtime,until);
    }
    /** Find a bounded overnight break in filtered usage; both edges must be observed. */
    static Span sleep(List<Span> phone,long evening,long midnight,long lastBedtime,long noon){
        List<Span> used=union(phone,evening,noon);Span best=null;
        for(int i=1;i<used.size();i++){
            long a=used.get(i-1).end,b=used.get(i).start;
            if(a>=lastBedtime||b<midnight||b<=a)continue;
            if(best==null||b-a>best.end-best.start)best=new Span(a,b);
        }return best;
    }
    static List<Span> union(List<Span> input,long begin,long end) {
        List<Span> clipped=new ArrayList<>(),result=new ArrayList<>();
        for(Span s:input){long a=Math.max(begin,s.start),b=Math.min(end,s.end);if(a<b)clipped.add(new Span(a,b));}
        clipped.sort(Comparator.comparingLong(Span::start));
        for(Span s:clipped){
            if(result.isEmpty()||s.start>result.get(result.size()-1).end)result.add(s);
            else {Span last=result.remove(result.size()-1);result.add(new Span(last.start,Math.max(last.end,s.end)));}
        }return result;
    }
    static List<Span> blanks(List<Span> covered,long begin,long end) {
        List<Span> result=new ArrayList<>();long cursor=begin;
        for(Span s:union(covered,begin,end)){if(cursor<s.start)result.add(new Span(cursor,s.start));cursor=s.end;}
        if(cursor<end)result.add(new Span(cursor,end));return result;
    }
    static long duration(List<Span> spans,long begin,long end){long total=0;for(Span s:union(spans,begin,end))total+=s.end-s.start;return total;}
    static List<Item> mergeApps(List<Item> input,long begin,long end){
        Map<String,List<Span>> grouped=new LinkedHashMap<>();Map<String,String> labels=new HashMap<>();
        for(Item item:input){grouped.computeIfAbsent(item.pkg,k->new ArrayList<>()).add(new Span(item.start,item.end));labels.put(item.pkg,item.label);}
        List<Item> result=new ArrayList<>();
        for(String pkg:grouped.keySet())for(Span s:union(grouped.get(pkg),begin,end))result.add(new Item(pkg,labels.get(pkg),s.start,s.end));
        result.sort(Comparator.comparingLong(Item::start).thenComparing(Item::pkg));return result;
    }
}
