package org.seckill.exception;

/**
 * @Author:陈郭石
 * @description: 重复秒杀异常（运行期异常）
 * @Date:Created in 21:56 2019/6/14
 */
public class RepeatKillException extends SeckillException{
    public RepeatKillException(String message) {
        super(message);
    }

    public RepeatKillException(String message, Throwable cause) {
        super(message, cause);
    }
}
