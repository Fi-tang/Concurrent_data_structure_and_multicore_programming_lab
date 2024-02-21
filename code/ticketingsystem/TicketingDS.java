package ticketingsystem;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


class Qnode{
    public AtomicBoolean locked = new AtomicBoolean(true);

}

class CLHLock {
    AtomicReference<Qnode> tail = new AtomicReference<Qnode>();
    ThreadLocal<Qnode> myNode = new ThreadLocal<Qnode>();
    ThreadLocal<Qnode> myPred = new ThreadLocal<Qnode>();

    public CLHLock(){
        tail.set(new Qnode());
        tail.get().locked.set(false);
    }

    public void lock(){
        if(myNode.get() == null)    myNode.set(new Qnode());
        myNode.get().locked.set(true);
        Qnode pred = tail.getAndSet(myNode.get());
        myPred.set(pred);
        while(pred.locked.get()){};
    }

    public void unlock(){
        myNode.get().locked.set(false);
        myNode.set(myPred.get());
    }
}


public class TicketingDS implements TicketingSystem{
    AtomicLong Counter;
    public int routenum;    // 5
    public int coachnum;    // 8 [coach][coach][coach][coach][coach][coach][coach][coach]
    public int seatnum;     // 100 seat
    public int stationnum;  // 10
    public int threadnum;   // 16 concurrent

    public int BitMapColumn;

    // record all seat state
    public static CLHLock[] lock;

    // BitMap [] require routenum's bitmap
    // BitMap[coachnum * seatnum][stationnum]

    public static ArrayList<long[][]> BitMapTotal;
    public static long[][] BitMap;

    public TicketingDS(int routenum,int coachnum,int seatnum, int stationnum, int threadnum){
        this.Counter = new AtomicLong(0);
        this.routenum = routenum;
        this.coachnum = coachnum;
        this.seatnum = seatnum;
        this.stationnum = stationnum;
        this.threadnum = threadnum;
        this.BitMapColumn = (coachnum * seatnum) / 64;

        lock = new CLHLock[routenum + 1];
        for(int i = 0; i <= routenum; i++){
            lock[i] = new CLHLock();
        }

        BitMapTotal = new ArrayList<long[][]>();

        for(int i = 0; i <= routenum; i++){
            long[][] BitMapToadd = new long[BitMapColumn + 1][stationnum + 1];
            BitMapTotal.add(BitMapToadd);
        }

        // PrintCheck
        // now we need to get column and the corresponding seat
        // the x-th(start at 1)coach the y-th seat(start at 1) lies at the ()'s byte(start at 0)
        // the x-th coach, the y-th seat lies at the
        /**
         *  9 coach 100 seat
         *  [0]  1: 1 - 64                  [1] 1: 65 - 100(36)     2: 1 - 28
         *  [2]  2: 29 - 92                 [3] 2: 93 - 100(8)      3: 1 - 56
         *  [4]  3: 57 - 100(44) 4: 1 - 20  [5] 4: 21 - 84
         *  [6]  4: 85 - 100(16) 5: 1 - 48  [7]  5: 49 - 100(52) 6: 1 - 12
         *  [8]  6: 13 - 76                 [9] 6: 77 - 100(24) 7: 1 - 40
         *  [10] 7: 41- 100(60)  8: 1 - 4   [11] 8: 5 - 68
         *  [12] 8: 69 - 100(32) 9: 1 - 32  [13] 9: 33 - 96
         *  [14] 9: 97- 100(4) add 60' 1
         * The Left function:
         * before, we have (x - 1) * 100 + (y - 1)
         * long [9][station][62]
         * **/
        // -----> Left to right : Given x th coach y th seat --> get (longPosition - 1) [long] [bitePosition - 1]
        int x = 9, y = 97;
        int longNumber = (x - 1) * seatnum + (y - 1);
        int longBegin = (longNumber) >>> 6; // >>> 6 === / 64
        int longBias = (longNumber & 0x3F);
        // System.out.println(x + " th coach  " + y + " th seat lie at " + longBegin + " byte " + longBias + " position(start all at 0)");

        // ----> right to Left: Given m th[long] and n th[bitePosition], get x th coach and y th seat
        int m = 7, n = 0;
        int nNumber = m * 64 + n;   // m = 3, n = 0, represent the 4 [long] position[1]
        int coachBegin = nNumber/ seatnum + 1;
        int seatBegin = nNumber % seatnum + 1;
        // System.out.println( m + " [long] and the " + n + " bias lie at coach: " + coachBegin  + " seat: " + seatBegin);
        // --------------------------------------------------------------------------------------------------------
        for(int i = 0; i <= routenum; i++){
            for(int k = 0; k < BitMapColumn + 1; k++){
                BitMapTotal.get(i)[k][0] = 0xFFFFFFFFFFFFFFFFL;         // line 0 is useless, fill 1
            }
        }

        int last = BitMapColumn;
        int start = coachnum * seatnum;                         // 900
        int end = (BitMapColumn + 1) * 64;   // 960 the last part to fill the last 60
        long originlValue = 0xFFFFFFFFFFFFFFFFL;
        // need to append 60 '[1]
        long secondValue = originlValue >>> (64 - (end - start));

        for(int i = 0; i <= routenum; i++){
            for(int l = 1; l <= stationnum; l++){
                // The last map
                BitMapTotal.get(i)[last][l] = secondValue;      // padding part is useless, fill 1
            }
        }
    }

     @Override
    public Ticket buyTicket(String passenger, int route, int departure, int arrival){
        lock[route].lock();
        long compare = 0L;
        long compareMinus = 0L;
        long compareNOR = 0L;

        for(int k = 0; k < BitMapColumn + 1; k++){
            compare = 0L;
            for(int l = departure; l < arrival; l++){
                compare = compare | BitMapTotal.get(route)[k][l];
            }
            if(compare == ~0L && k < BitMapColumn){
                continue;
            }
            if(compare == ~0L && k == BitMapColumn){
                lock[route].unlock();
                return null;
            }
            compareMinus = (~compare) - 1;
            compareNOR = (~compare) ^ compareMinus;
            int Left = Long.bitCount(compareNOR);
            Ticket buyItem = new Ticket();
            buyItem.tid = Counter.getAndIncrement();
            buyItem.passenger = passenger;
            buyItem.route = route;
            // the k th [long], and the bias = 64 - Left;
            buyItem.coach = (k * 64 + (64 - Left))/ seatnum + 1;
            buyItem.seat = (k * 64 + (64 - Left)) % seatnum + 1;
            buyItem.departure = departure;
            buyItem.arrival = arrival;
            // Assign 1
            long originValue = 1L;
            long shiftValue = originValue << (Left - 1);
            for(int ll = departure; ll < arrival; ll++){
                BitMapTotal.get(route)[k][ll] = BitMapTotal.get(route)[k][ll] | shiftValue;
            }

            lock[route].unlock();
            return buyItem;
        }
        lock[route].unlock();
        return null;
    }


    
    @Override
    public int inquiry(int route, int departure, int arrival){
        lock[route].lock();
        int totalLeft = 0;
        long compare = 0L;
        for(int k = 0; k < BitMapColumn + 1; k++) {
            compare = 0L;
            for(int l = departure; l < arrival; l++){
                compare = compare | BitMapTotal.get(route)[k][l];
            }
            totalLeft += Long.bitCount(~compare);
        }
        lock[route].unlock();
        return totalLeft;
    }

    @Override
    public boolean refundTicket(Ticket ticket){
        int Refroute = ticket.route;
        int Refcoach = ticket.coach;
        int Refdeparture = ticket.departure;
        int Refarrival = ticket.arrival;
        int RefSeat = ticket.seat;
        int longNumber = (Refcoach - 1) * seatnum + RefSeat - 1;
        int longBegin = (longNumber) >>> 6;
        int longBias = (longNumber & 0x3F);
        long originalValue = 1L;
        long secondValue = originalValue << (64 - longBias - 1);
        long startCompare = 0L;

        lock[ticket.route].lock();
        // According to coach and ticket
        for(int l = Refdeparture; l < Refarrival; l++){
            startCompare = startCompare | BitMapTotal.get(Refroute)[longBegin][l];
        }

        if((startCompare & secondValue) != secondValue){
            lock[ticket.route].unlock();
            return false;
        }

        for(int l = Refdeparture; l < Refarrival; l++){
            BitMapTotal.get(Refroute)[longBegin][l] = BitMapTotal.get(Refroute)[longBegin][l] & ~secondValue;
        }
        lock[ticket.route].unlock();
        return true;
    }

    @Override
    public boolean buyTicketReplay(Ticket ticket){
        return false;
    }
    @Override
    public boolean refundTicketReplay(Ticket ticket){
        return false;
    }

}