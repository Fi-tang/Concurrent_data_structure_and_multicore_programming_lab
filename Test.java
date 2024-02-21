import java.lang.InterruptedException;
import java.util.concurrent.atomic.*;
import java.lang.Math.*;

interface Lock{
    public void lock();
    public void unlock();
}

class TASlock implements Lock {
    AtomicBoolean state = new AtomicBoolean(false); //锁的状态
    public void lock() {
        while (state.getAndSet(true)) {} //空转，直到获得锁（状态从假到真）
    }
    public void unlock() {
        state.set(false); //释放锁
    }
}

class TTASlock implements Lock{
    AtomicBoolean state = new AtomicBoolean(false);
    public void lock() {
        while (true) {
            while (state.get()) {} //空转读，直到锁被释放
            if (!state.getAndSet(true))
                return; //加锁成功
        }
    }
    public void unlock() {
        state.set(false); //释放锁
    }
}


class Backoff implements Lock {
    AtomicBoolean state = new AtomicBoolean(false);
    public static final int MIN_DELAY = 90;
    public static final int MAX_DELAY = 1000;
    public int randomint = 0;

    public void lock() {
        int delay = MIN_DELAY; //回退时长的上限
        while (true) {
            while (state.get()) {} //等待锁空闲
            if (!state.getAndSet(true))
                return; //获得锁

            for(int i = 0; i < Math.random() * delay; i++){
                randomint += 1;
            }
            ; //随机回退一段时间
            if (delay < MAX_DELAY)
                delay = 2 * delay; //回退时长的上限加倍
        }
    }

    public void unlock(){
        state.set(false);
    }
}


class ALock implements Lock {
    boolean[] flags;
    public int ArraySize;
    AtomicInteger next = new AtomicInteger(0); //下一个等待 slot
    ThreadLocal<Integer> mySlot = new ThreadLocal<Integer>();

    ALock(int ArraySize){
        this.ArraySize = ArraySize;
        flags = new boolean[ArraySize];
        flags[0] = true;
        for(int i = 1; i < ArraySize; i++){
            flags[i] = false;
        }
    }

    public void lock(){
        mySlot.set(next.getAndIncrement()); //设置下一个等待 slot
        while(!flags[mySlot.get() % ArraySize]){}; //空转，忙等在自己的 slot 上
        flags[mySlot.get() % ArraySize]=false; //获得锁，占用中
    }
    public void unlock(){
        flags[(mySlot.get() +1)% ArraySize]=true; //下一个线程可用
    }
}


class Qnode {
    AtomicBoolean locked = new AtomicBoolean(true); //新加入的结点
}

class CLHLock implements Lock {
    AtomicReference<Qnode> tail = new AtomicReference<Qnode>();
    ThreadLocal<Qnode> myNode = new ThreadLocal<Qnode>(); //线程 Qnode
    ThreadLocal<Qnode> myPred  = new ThreadLocal<Qnode>();

    CLHLock(){
        Qnode Qnode = new Qnode();//队尾
        tail.set(Qnode);
        Qnode.locked.set(false); //队尾初始时，锁可用
    }

    public void lock() {
        Qnode QQnode = new Qnode();
        myNode.set(QQnode);
        QQnode.locked.set(true);

        Qnode pred = tail.getAndSet(myNode.get()); //加入队尾
        myPred.set(pred);
        while (pred.locked.get()) {}; //空转在 pred 结点
    }
    public void unlock() {
        myNode.get().locked.set(false); //通知后继线程
        myNode.set(myPred.get()); //回收前驱 Qnode 结点，并复用
    }
}


class MCSnode {
    volatile boolean locked = false;
    MCSnode next = null;
}

class MCSLock implements Lock {
    AtomicReference<MCSnode> tail = new AtomicReference<MCSnode>(null);
    ThreadLocal<MCSnode> myNode = new ThreadLocal<MCSnode>();


    public void lock() {
        MCSnode MMCSnode = new MCSnode();
        myNode.set(MMCSnode);
        MMCSnode.locked = false;

        MCSnode pred = tail.getAndSet(myNode.get()); //加入队尾
        if (pred != null) { //若队列不空
            myNode.get().locked = true; //准备空转
            pred.next = myNode.get(); //将前驱结点的 next 指向新结点
            while (myNode.get().locked) {} //在新结点上空转
        }
    }
    public void unlock() {
        if (myNode.get().next == null) {
            if (tail.compareAndSet(myNode.get(), null)) //没有后继线程
                return;
            while (myNode.get().next == null) {} //等待后继结点加入队尾
        }
        myNode.get().next.locked = false; //通知后继结点
    }
}

class ThreadDemo extends Thread {
    private Thread t;
    private int Average;
    public static int counter = 0;
    public Lock lock;

    ThreadDemo(int average , Lock lock) {
        Average = average;
        this.lock = lock;
    }

    public void run() {
        for(int i = Average; i >= 1; i--) {
            lock.lock();
            counter += 1;
            lock.unlock();
        }
        System.out.println("counter: " +  counter);
    }
}


public class Test{
    public static void main(String args[]){
        int threadNumer = 16;
        ThreadDemo[] ThreadArray = new ThreadDemo[threadNumer];

        TASlock TAS = new TASlock();
        TTASlock TTAS = new TTASlock();
        Backoff BackoffLock = new Backoff();
        ALock   ALocklock = new ALock(threadNumer);
        CLHLock CLHLocklock = new CLHLock();
        MCSLock MCSLocklock = new MCSLock();


        long startTime = System.nanoTime();
        for(int i = 0; i < threadNumer; i++) {
            ThreadArray[i] = new ThreadDemo(1000000 / threadNumer, MCSLocklock);
        }
        for(int i = 0; i < threadNumer; i++) {
            ThreadArray[i].start();
        }
        for(int i = 0; i < threadNumer;i++){
            try{
                ThreadArray[i].join();
            }catch(InterruptedException e){

            }

        }
        long endTime = System.nanoTime();
        System.out.println(endTime - startTime);
    }
}