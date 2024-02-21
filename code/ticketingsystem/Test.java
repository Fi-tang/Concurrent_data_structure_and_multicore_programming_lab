package ticketingsystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Scanner;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;


class ThreadToAllocate{
    private AtomicInteger newId = new AtomicInteger(0);

    private ThreadLocal<Integer> threadGetId =
            new ThreadLocal<Integer>(){
                @Override protected Integer initialValue(){
                    return newId.getAndIncrement();
                }
            };
    public  int get(){
        return threadGetId.get();
    }
}

public class Test {
    static int refRatio = 10;
    static int buyRatio = 20;
    static int inqRatio = 30;
    static int testnum = 64000000;
    static int[] CountBuy;
    static int[] CountInq;
    static int[] CountRef;
    static long[] BuyTime;
    static long[] RefTime;
    static long[] InqTime;
    static ThreadToAllocate threadId;
    static ArrayList<List<Ticket>> hasBeensold;
    static long GlobalBuy;
    static long GlobalInq;
    static long GlobalRef;
    static long GlobalThroughput;


    public static void main(String[] args) throws InterruptedException {
        // *******************************************************
        // routenum, coachnum, seatnum, stationnum, threadnum
        int routenum = Integer.parseInt(args[0]);
        int coachnum = Integer.parseInt(args[1]);
        int seatnum = Integer.parseInt(args[2]);
        int stationnum = Integer.parseInt(args[3]);
        int threadnum = Integer.parseInt(args[4]);
   
        int turnNum = 5;
        for(int turn = 0; turn < turnNum; turn++){
            CountBuy = new int[threadnum];
            CountInq = new int[threadnum];
            CountRef = new int[threadnum];
            BuyTime = new long[threadnum];
            RefTime = new long[threadnum];
            InqTime = new long[threadnum];

            final TicketingDS tds = new TicketingDS(routenum,coachnum,seatnum,stationnum,threadnum);
            // ***********************************************************************
            // initialization
            hasBeensold = new ArrayList<List<Ticket>>();
            threadId = new ThreadToAllocate();
            // 4, 8 , 16, 32, 64
            Thread[] threadsToCom = new Thread[threadnum];
            // 30 : 10 : 60 method Call
            // verage conduct
            int Averagecount = testnum / threadnum;
            // Initializting ThreadLocal Variables
            for(int i = 0; i < threadnum; i++){
                List<Ticket> threadBuyingTicket = new ArrayList<Ticket>();
                hasBeensold.add(threadBuyingTicket);
            }

            for(int i = 0; i < threadnum; i++){
                threadsToCom[i] = new Thread(()-> {
                    Random rand = new Random();
                    long threadBuyTime = 0;
                    long threadRefTime = 0;
                    long threadInqTime = 0;
                    int countBuy = 0;
                    int countRef = 0;
                    int countInq = 0;
                    // methodList = [buyTicket | buyTicket | buyTicket | refund | inquiry | inquiry | inquiry | inquiry | inquiry | inquiry]
                    for(int testRound = 0; testRound < Averagecount; testRound++){
                        int methodCount = rand.nextInt(10);
                        if(methodCount < 3){    // buy
                            countBuy += 1;
                            Ticket ticketBuy = new Ticket();
                            String passenger = "passenger" + rand.nextInt(testnum);
                            int route = rand.nextInt(routenum) + 1;
                            int departure = rand.nextInt(stationnum - 1) + 1;
                            int arrival = departure + rand.nextInt(stationnum - departure) + 1;
                            // preparing for buy method

                            long buyTimeStart = System.nanoTime();
                            ticketBuy = tds.buyTicket(passenger, route, departure, arrival);
                            long buyTimeEnd = System.nanoTime();
                            threadBuyTime += buyTimeEnd - buyTimeStart;

                            hasBeensold.get(threadId.get()).add(ticketBuy);
                        }

                        else if(methodCount == 3){  // refund
                            if(hasBeensold.get(threadId.get()).size() == 0){
                                continue;
                            }
                            // ThreadToAllocate --> thread
                            int n = rand.nextInt(hasBeensold.get(threadId.get()).size());
                            Ticket ticketRefund = hasBeensold.get(threadId.get()).remove(n);
                            // prepare
                            if(ticketRefund == null){
                                continue;
                            }
                            countRef += 1;
                            long refundTimeStart = System.nanoTime();
                            boolean flag = tds.refundTicket(ticketRefund);
                            long refundTimeEnd = System.nanoTime();
                            threadRefTime += refundTimeEnd - refundTimeStart;
                        }

                        else{   // inquiry
                            countInq += 1;
                            Ticket inqTicket = new Ticket();
                            inqTicket.passenger = "Passenger" + rand.nextInt(testnum);
                            inqTicket.route = rand.nextInt(routenum) + 1;
                            inqTicket.departure = rand.nextInt(stationnum - 1) + 1;
                            inqTicket.arrival = inqTicket.departure + rand.nextInt(stationnum - inqTicket.departure) + 1;
                            long inqTimeStart = System.nanoTime();
                            inqTicket.seat = tds.inquiry(inqTicket.route, inqTicket.departure, inqTicket.arrival);
                            long inqTimeEnd = System.nanoTime();
                            threadInqTime += inqTimeEnd - inqTimeStart;
                        }
                    }
                    BuyTime[threadId.get()] = threadBuyTime;
                    RefTime[threadId.get()] = threadRefTime;
                    InqTime[threadId.get()] = threadInqTime;
                    CountRef[threadId.get()] = countRef;
                    CountBuy[threadId.get()] = countBuy;
                    CountInq[threadId.get()] = countInq;
                });
            }

            long finalstart = System.nanoTime();
            for(int i = 0; i < threadnum; i++){
                threadsToCom[i].start();
            }

            for(int i = 0; i < threadnum; i++){
                threadsToCom[i].join();
            }
            long finalend = System.nanoTime();

            int totalBuy = 0;
            int totalInq = 0;
            int totalRef = 0;
            long totalBuyTime = 0;
            long totalInqTime = 0;
            long totalRefTime = 0;
            for(int i = 0; i < threadnum; i++){
                totalBuy += CountBuy[i];
                totalInq += CountInq[i];
                totalRef += CountRef[i];
                totalBuyTime += BuyTime[i];
                totalInqTime += InqTime[i];
                totalRefTime += RefTime[i];
            }
            GlobalBuy += totalBuyTime / totalBuy;
            GlobalInq += totalInqTime / totalInq;
            GlobalRef += totalRefTime / totalRef;
            GlobalThroughput += 1000000L *(totalBuy + totalInq + totalRef)/(finalend - finalstart);
        }
        System.out.println("Average BuyTime = " + GlobalBuy / turnNum);
        System.out.println("Average RefTime = " + GlobalInq/ turnNum);
        System.out.println("Average InqTime = " + GlobalRef / turnNum);
        System.out.println("Throughput = " +  GlobalThroughput/turnNum);
    }
}