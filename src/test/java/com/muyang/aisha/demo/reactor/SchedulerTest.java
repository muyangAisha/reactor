package com.muyang.aisha.demo.reactor;

import org.junit.Test;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * Created by chenyixiong on 2018/11/21.
 */
public class SchedulerTest {

    private Logger log = LoggerFactory.getLogger(SchedulerTest.class);

    /**
     * 当前线程（Schedulers.immediate()）；
     * 可重用的单线程（Schedulers.single()）。注意，这个方法对所有调用者都提供同一个线程来使用， 直到该调度器被废弃。如果你想使用独占的线程，请使用Schedulers.newSingle()；
     * 弹性线程池（Schedulers.elastic()）。它根据需要创建一个线程池，重用空闲线程。线程池如果空闲时间过长 （默认为 60s）就会被废弃。对于 I/O 阻塞的场景比较适用。Schedulers.elastic()能够方便地给一个阻塞 的任务分配它自己的线程，从而不会妨碍其他任务和资源；
     * 固定大小线程池（Schedulers.parallel()），所创建线程池的大小与CPU个数等同；
     * 自定义线程池（Schedulers.fromExecutorService(ExecutorService)）基于自定义的ExecutorService创建 Scheduler（虽然不太建议，不过你也可以使用Executor来创建）。
     * Schedulers类已经预先创建了几种常用的线程池：使用single()、elastic()和parallel()方法可以分别使用内置的单线程、弹性线程池和固定大小线程池。如果想创建新的线程池，可以使用newSingle()、newElastic()和newParallel()方法。
     * <p>
     * Executors提供的几种线程池在Reactor中都支持：
     * <p>
     * Schedulers.single()和Schedulers.newSingle()对应Executors.newSingleThreadExecutor()；
     * Schedulers.elastic()和Schedulers.newElastic()对应Executors.newCachedThreadPool()；
     * Schedulers.parallel()和Schedulers.newParallel()对应Executors.newFixedThreadPool()；
     * 下一章会介绍到，Schedulers提供的以上三种调度器底层都是基于ScheduledExecutorService的，因此都是支持任务定时和周期性执行的；
     * Flux和Mono的调度操作符subscribeOn和publishOn支持work-stealing。
     */

    private String getStringSync() {
        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return "Hello, Reactor!";
    }

    @Test
    public void testSyncToAsync() throws InterruptedException {

        System.out.println("打印了1句话");

        CountDownLatch countDownLatch = new CountDownLatch(1);

        System.out.println("打印了2句话");
        Mono.fromCallable(() -> getStringSync())
                .subscribeOn(Schedulers.elastic())//使用subscribeOn将任务调度到Schedulers内置的弹性线程池执行，弹性线程池会为Callable的执行任务分配一个单独的线程。
                .subscribe(System.out::println, null, countDownLatch::countDown);


        System.out.println("打印了3句话");
        countDownLatch.await(10, TimeUnit.SECONDS);
        System.out.println("打印了4句话");
    }

    /**
     * 假设与上图对应的代码是：
     * Flux.range(1, 1000)
     * .map(…)
     * .publishOn(Schedulers.elastic()).filter(…)
     * .publishOn(Schedulers.parallel()).flatMap(…)
     * .subscribeOn(Schedulers.single())
     * 如图所示，publishOn会影响链中其后的操作符，比如第一个publishOn调整调度器为elastic，则filter的处理操作是在弹性线程池中执行的；
     * 同理，flatMap是执行在固定大小的parallel线程池中的；
     * subscribeOn无论出现在什么位置，都只影响源头的执行环境，也就是range方法是执行在单线程中的，直至被第一个publishOn切换调度器之前，
     * 所以range后的map也在单线程中执行。
     */

    @Test
    public void subscribeError() {
        Flux.range(1, 6)
                .map(i -> 10 / (i - 3)) //当i为3时会导致异常。
                .map(i -> i * i)
                .subscribe(System.out::println, System.err::println);


        Flux.range(1, 6)
                .map(i -> 10 / (i - 3))
                .onErrorReturn(0) //当发生异常时提供一个缺省值0
                .map(i -> i * i)
                .subscribe(System.out::println, System.err::println);

        Flux.range(1, 6)
                .map(i -> 10 / (i - 3))
                .onErrorResume(e -> Mono.just(new Random().nextInt(6))) //提供新的数据流,捕获并执行一个异常处理方法或计算一个候补值来顶替
                .map(i -> i * i)
                .subscribe(System.out::println, System.err::println);


        //捕获，并再包装为某一个业务相关的异常，然后再抛出业务异常（1）
        /*Flux.just("timeout1")
                .flatMap(t -> Mono.just(getException(t)))
                .onErrorMap(original -> new MyException("My test Exception 1", original))
                .subscribe(System.out::println);*/

        //捕获，并再包装为某一个业务相关的异常，然后再抛出业务异常（2）
        /*Flux.just("timeout1")
                .flatMap(t -> Mono.just(getException(t)))
                .onErrorResume(original -> Flux.error(new MyException("My test Exception 2", original)))
                .subscribe(System.out::println, System.err::println);*/

        Flux.just("socket timeout", "connection timeout")
                .flatMap(k -> Mono.just(getException(k)))
                .doOnError(e -> log.info("uh oh, falling back, service failed for key " + e))
                .onErrorResume(e -> Mono.just("myErrorResume"))
                .subscribe(System.out::println);
    }

    /**
     * Flux.using(
     * () -> getResource(),    // 第一个参数获取资源
     * resource -> Flux.just(resource.getAll()),   // 第二个参数利用资源生成数据流
     * MyResource::clean   // 第三个参数最终清理资源。
     * );
     */

    @Test
    public void doFinally() {
        /**
         * 另一方面， doFinally在序列终止（无论是 onComplete、onError还是取消）的时候被执行，
         * 并且能够判断是什么类型的终止事件（完成、错误还是取消），以便进行针对性的清理。
         */

        LongAdder staticCancel = new LongAdder();

        System.out.println(staticCancel.intValue());

        Flux<String> flux = Flux.just("foo", "bar")
                .doFinally(type -> {
                    if (type == SignalType.CANCEL) { //doFinally用SignalType检查了终止信号的类型
                        staticCancel.increment(); //如果是取消，那么统计数据自增
                    }
                })
                .take(1); //take(1)能够在发出1个元素后取消流

        System.out.println(staticCancel.intValue());

        flux.subscribe(System.out::println);
        System.out.println(staticCancel.intValue());
    }

    @Test
    public void retry() throws InterruptedException {
        /**
         * retry对于上游Flux是采取的重订阅（re-subscribing）的方式，因此重试之后实际上已经一个不同的序列了，
         * 发出错误信号的序列仍然是终止了的。
         */

        Flux.range(1, 6)
                .map(i -> 10 / (3 - i))
                .retry(1)
                .subscribe(System.out::println, System.err::println);
        Thread.sleep(100);
        /**
         * 可见，retry不过是再一次从新订阅了原始的数据流，从1开始。
         * 第二次，由于异常再次出现，便将异常传递到下游了
         */
    }


    @Test
    public void testBackpressure() {
        /**
         * Subscriber就需要通过request(n)的方法来告知上游它的需求速度
         */

        Flux.range(1, 6)
                .doOnRequest(n -> System.out.println("Request " + n + " values...")) //在每次request的时候打印request个数
                .subscribe(new BaseSubscriber<Integer>() { //通过重写BaseSubscriber的方法来自定义Subscriber；
                    @Override
                    protected void hookOnSubscribe(Subscription subscription) { //hookOnSubscribe定义在订阅的时候执行的操作
//                        super.hookOnSubscribe(subscription);
                        System.out.println("Subscribed and make a request...");
                        request(2); //订阅时首先向上游请求1个元素；
                    }

                    @Override
                    protected void hookOnNext(Integer value) { //hookOnNext定义每次在收到一个元素的时候的操作
//                        super.hookOnNext(value);
                        try {
                            TimeUnit.SECONDS.sleep(1); //sleep 1秒钟来模拟慢的Subscriber
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        System.out.println("Get value [" + value + "]");
                        request(1); //每次处理完1个元素后再请求1个
                    }
                });
    }


    private String getException(String str) {
        int i = 10 / 0;
        return "getException " + str;
    }

    @Test
    public void test() {
        System.out.println(2457 % 32);
        System.out.println((2457 / 32) % 32);
    }
}
