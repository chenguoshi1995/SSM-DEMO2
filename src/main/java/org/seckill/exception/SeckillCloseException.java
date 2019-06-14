package org.seckill.exception;

/**
 * @Author:陈郭石
 * @description: 秒杀关闭异常
 * @Date:Created in 21:57 2019/6/14
 */
public class SeckillCloseException extends SeckillException{
    public SeckillCloseException(String message) {
        super(message);
    }

    public SeckillCloseException(String message, Throwable cause) {
        super(message, cause);
    }
}
