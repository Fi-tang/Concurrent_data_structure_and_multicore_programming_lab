**写在最前**
本分支的内容为，实现用于列车售票的可线性化并发数据结构。
myproject 文件夹为 startcode, code 文件夹中为实现的内容。
整个设计的核心在 **code**文件夹下的**ticketingsystem**子文件夹中的 TicketingDS.java,
主要采用 BitMap(位图）优化查询时间, 实现两组车次和座位的转化, 结合并发的 CLHLock 进行加锁控制。

整个实验的设计在 **code** 文件夹下的性能评测报告.pdf 中。

场景设置为: 5趟车次(routenum)，每列车共有8节车厢(coachnum)，每节车厢有100个座位(seatnum),
每个车次经停站的数量为10(stationnum), 并发购票的线程数为 16(threadnum)。
系统中同时存在 threadnum(16)个线程，每个线程是一个票务代理，按照 60%查询，30%购票，10%退票的比率
反复调用 TicketingDS 类的3种方法若干次(总共为10000 次)。
按照线程数为4，8，16，32，64 的情况得到总的平均执行时间。
在每个线程执行100万条操作时，线程数为 64 时，达到了 8650 的吞吐量，结果如下:
![result](https://github.com/Fi-tang/Concurrent_data_structure_and_multicore_programming_lab/blob/TicketingDS/result.PNG)

