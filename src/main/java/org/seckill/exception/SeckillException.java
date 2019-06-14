package org.seckill.exception;

/**
 * @Author:陈郭石
 * @description: 秒杀相关异常
 * @Date:Created in 21:59 2019/6/14
 */
public class SeckillException extends RuntimeException{
    public SeckillException(String message) {
        super(message);
    }

    public SeckillException(String message, Throwable cause) {
        super(message, cause);
    }
}
