**实验内容**
参照引用文献，对并发锁对象进行性能分析和对比
参考设计如下:
1. 并发对象: 计数器(counter), 初始值为 0
2. 并发任务: n线程互斥访问并发计数器，总共访问1百万次临界区: counter = counter + 1.
总的计算量是固定的(1百万次操作，确保计数器的最终值是1百万）
展示随着线程数量的变化，这一百万次操作所需花费时间的变化。
使用 universal construction(通用构造）实现并发锁，对比分析性能。

下图展示了 TAS, TTAS, Backoff(回退锁), ALock, CLHLock 以及 MCSLock 的一百万次操作所花费的时间。
横坐标代表并发的线程数量分别从1，2，4，8，到16线程。
![result](https://github.com/Fi-tang/Concurrent_data_structure_and_multicore_programming_lab/blob/LockTest/result.png)
