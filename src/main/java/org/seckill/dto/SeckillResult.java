package org.seckill.dto;

/**
 * @Author:陈郭石
 * @description: work
 * @Date:Created in 16:58 2019/6/14
 */
public class SeckillResult<T> {
    
	
	//秒杀结果   是否成功
    private boolean success;
    
    //秒杀结果状态  将秒杀结果进行包装
    private T data;
    
    //错误信息
    private String error;

    public SeckillResult(boolean success, T data) {
        this.success = success;
        this.data = data;
    }

    public SeckillResult(boolean success, String error) {
        this.success = success;
        this.error = error;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
