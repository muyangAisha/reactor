package com.muyang.aisha.demo.reactor;

import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Created by chenyixiong on 2018/11/13.
 */
public class BaseTest {

    @Test
    /**
     * Flux和Mono都可以发出三种“数据信号”：元素值、错误信号、完成信号
     */
    public void just() {
        // Flux和Mono提供了多种创建数据流的方法，just就是一种比较直接的声明数据流的方式，其参数就是数据元素。
        Flux.just(1, 2, 3, 4, 5, 6);
        Mono.just(1);

        // 还可以通过如下方式声明（分别基于数组、集合和Stream生成）
        Integer[] array = new Integer[]{1, 2, 3, 4, 5, 6};
        Flux.fromArray(array);

        List<Integer> list = Arrays.asList(array);
        Flux.fromIterable(list);

        Stream<Integer> stream = list.stream();
        Flux.fromStream(stream);

        // 只有完成信号的空数据流
        Flux.just();
        Flux.empty();
        Mono.empty();
        Mono.justOrEmpty(Optional.empty());

        // 只有错误信号的数据流
        Flux.error(new Exception("some error"));
        Mono.error(new Exception("some error"));

        // 订阅前什么都不会发生
        Flux.just(1, 2, 3, 4, 5, 6).subscribe(System.out::print);
        System.out.println();
        Mono.just(1).subscribe(System.out::print);
        System.out.println();

        // 订阅并定义对正常数据元素、错误信号和完成信号的处理，以及订阅发生时的处理逻辑
        /*subscribe(Consumer<? super T> consumer,
                Consumer<? super Throwable> errorConsumer,
                Runnable completeConsumer,
                Consumer<? super Subscription> subscriptionConsumer);*/
        Flux.just(1, 2, 3, 4, 5, 6).subscribe(
                System.out::println,
                System.err::println,
                () -> System.out.println("complete!"));


        //再举一个有错误信号的例子
        Mono.error(new Exception("some error")).subscribe(
                System.out::print,
                System.err::print,
                () -> System.out.println("complete!")
        );

    }

    /**
     * 基本的单元测试工具
     */
    @Test
    public void stepVerifierTest() {
        StepVerifier.create(Flux.just(1, 2, 3, 4, 5, 6))
                .expectNext(1, 3, 3, 4, 5, 6)
                .expectComplete()
                .verify();
    }

    @Test
    public void fluxRange() {
        StepVerifier.create(Flux.range(1, 6)    // 1
                .map(i -> i * i))  // 2
                .expectNext(1, 4, 9, 16, 25, 36)    //3
                .verifyComplete();  //verifyComplete()相当于expectComplete().verify()。
    }

    @Test
    public void fluxFlatMap() {
        StepVerifier.create(
                Flux.just("flux", "mono")
                        .flatMap(s -> Flux.fromArray(s.split("\\s*"))
                                .delayElements(Duration.ofMillis(100))
                        ) //对每个元素延迟100ms；
                        .doOnNext(System.out::println)) //对每个元素进行打印（注doOnNext方法是“偷窥式”的方法，不会消费数据流）
                .expectNextCount(8)
                .verifyComplete();
    }


    @Test
    public void testZip() throws InterruptedException {
        Flux<String> fluxArray = Flux.fromArray("Zip two sources together, that is to say wait for all the sources to emit one element and combine these elements once into a Tuple2.".split("\\s+"));
        Flux<String> fluxArray2 = Flux.fromArray("Zip two sources together, that is to say wait for all the sources to emit one element and combine these elements once into a Tuple2.".split("\\s+"));


        CountDownLatch countDownLatch = new CountDownLatch(1);
        Flux.zip(
                fluxArray,
                fluxArray2,
                Flux.interval(Duration.ofMillis(200))// 使用Flux.interval声明一个每200ms发出一个元素的long数据流；因为zip操作是一对一的，故而将其与字符串流zip之后，字符串流也将具有同样的速度；
        ).subscribe(
                t -> System.out.println(t.getT1() + " ------ " + t.getT2() + " ====== " + t.getT3()),//zip之后的流中元素类型为Tuple2，使用getT1方法拿到字符串流的元素
                null,
                countDownLatch::countDown);
        countDownLatch.await(10, TimeUnit.SECONDS);//countDownLatch.await(10, TimeUnit.SECONDS)会等待countDown倒数至0，最多等待10秒钟。
    }

    /**
     *
     * Reactor中提供了非常丰富的操作符，除了以上几个常见的，还有：

     用于编程方式自定义生成数据流的create和generate等及其变体方法；
     用于“无副作用的peek”场景的doOnNext、doOnError、doOncomplete、doOnSubscribe、doOnCancel等及其变体方法；
     用于数据流转换的when、and/or、merge、concat、collect、count、repeat等及其变体方法；
     用于过滤/拣选的take、first、last、sample、skip、limitRequest等及其变体方法；
     用于错误处理的timeout、onErrorReturn、onErrorResume、doFinally、retryWhen等及其变体方法；
     用于分批的window、buffer、group等及其变体方法；
     用于线程调度的publishOn和subscribeOn方法。
     */

}
