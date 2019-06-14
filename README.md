# SSM框架实现高速秒杀商品秒杀商品
## 实现功能

- 秒杀接口暴露
- 执行秒杀
- 相关查询

## 数据库设计与编码

```
--创建数据库脚本
CREATE  DATABASE seckill;
--使用数据库
use seckill;
--创建秒杀库存表
CREATE TABLE seckill(
  `seckill_id` bigint NOT NULL AUTO_INCREMENT COMMENT '商品库存id',
  `name` varchar (120) NOT NULL COMMENT '商品名称',
  `number` int NOT NULL COMMENT '库存数量',
  `start_time` timestamp NOT NULL COMMENT '秒杀开启时间',
  `end_time` timestamp NOT NULL COMMENT '秒杀结束时间',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY(seckill_id),
  key idx_start_time(start_time),
  key idx_end_time(end_time),
  key idx_create_time(create_time)
)ENGINE=InnoDB AUTO_INCREMENT=1000 DEFAULT CHARSET=utf8 COMMENT='秒杀库存表';

--初始化数据
insert into
  seckill(name,number,start_time,end_time)
  values
    ('1元秒杀坚果tNT工作站',100,'2018-06-01 00:00:00','2018-06-02 00:00:00'),
    ('1元秒杀iphonex',100,'2018-06-01 00:00:00','2018-06-02 00:00:00'),
    ('1元秒杀坚果3',100,'2018-06-01 00:00:00','2018-06-02 00:00:00'),
    ('1元秒杀mac',100,'2018-06-01 00:00:00','2018-06-02 00:00:00');

--秒杀成功明细表
--用户登录认证的相关的信息
CREATE TABLE success_killed(
  `seckill_id` bigint NOT NULL COMMENT '秒杀商品id',
  `user_phone` bigint NOT NULL COMMENT '用户手机号',
  `state` bigint NOT NULL DEFAULT -1 COMMENT '状态表示：-1：无效，0：成功，1：已付款',
  `create_time` timestamp NOT NULL COMMENT '创建时间',
  PRIMARY KEY (seckill_id,user_phone),
  key idx_create_time(create_time)
)ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='秒杀成功明细表';
```

## 设计Dao接口

`SeckillDao接口`

```
public interface SeckillDao {
    /**
     * 减库存
     * @param seckillId
     * @param killTime
     * @return
     */
    int reduceNumber(@Param("seckillId") long seckillId, @Param("killTime") Date killTime);

    /**
     * 通过id查看秒杀商品
     * @param seckillId
     * @return
     */
    Seckill queryById(long seckillId);

    /**
     * 根据偏移量查询秒杀商品列表
     * @param offset
     * @param limit
     * java没有保存形参的记录，因此在运行期queryall(int offset,int limit)->queryAll(arg0,arg1)，因此加上@Param来告诉实际形参的名字
     */
    List<Seckill> queryAll(@Param("offset") int offset, @Param("limit") int limit);
}
```

`SuccessKilledDao`

```
public interface SuccessKilledDao {
    /**
     * 插入购买明细，可过滤重复（联合主键）
     * @param seckillId
     * @param userPhone
     * @return
     */
    int insertSuccessKilled(@Param("seckillId") long seckillId, @Param("userPhone") long userPhone);

    SuccessKilled queryByIdWithSeckill(@Param("seckillId") long seckillId,@Param("userPhone")long userPhone);
}
```

## 设计Service层接口及实现

`SeckillService接口`

```
public interface SeckillService {

    /**
     * 查询所有秒杀列表
     * @return
     */
    List<Seckill> getSeckillList();

    /**
     * 查询单个秒杀记录
     * @param seckillId
     * @return
     */
    Seckill getById(long seckillId);

    /**
     * 输出秒杀接口的地址
     * 秒杀开启时输出接口地址
     * 否则输出秒杀时间和系统时间
     * @param seckillId
     */
    Exposer exportSeckillUrl(long seckillId);

    /**
     * 执行秒杀操作
     * @param seckillId
     * @param userPhone
     * @param md5 匹配md5是否一致，判断用户秒杀地址是否正常
     */
    SeckillExecution executeSeckill(long seckillId, long userPhone, String md5) throws SeckillException,RepeatKillException,SeckillCloseException;
}
```

### 配置和开启事务(抛出运行期异常时则进行事务回滚)

```
<!--spring-service.xml-->
    <!--配置事务管理器-->
    <bean id="transactionManager" class="org.springframework.jdbc.datasource.DataSourceTransactionManager">
        <property name="dataSource" ref="dataSource"/>
    </bean>

    <!--配置基于注解的声明式事务-->
    <tx:annotation-driven transaction-manager="transactionManager"/>
```

```
	 @Override
    @Transactional
    public SeckillExecution executeSeckill(long seckillId, long userPhone, String md5) throws SeckillException, RepeatKillException, SeckillCloseException {
        if (md5 == null || !md5.equals(getMd5(seckillId))) {
            throw new SeckillException("seckill data rewrite");
        }
        //执行秒杀逻辑：减库存 + 记录购买行为
        Date nowTime = new Date();
        try {
            int updateCount = seckillDao.reduceNumber(seckillId, nowTime);
            if (updateCount <= 0) {
                //没有更新到记录
                throw new SeckillCloseException("seckill is closed");
            } else {
                //记录购买行为
                int insertCount = successKilledDao.insertSuccessKilled(seckillId, userPhone);
                if (insertCount <= 0) {
                    //重复秒杀
                    throw new RepeatKillException("seckill repeated");
                } else {
                    SuccessKilled successKilled = successKilledDao.queryByIdWithSeckill(seckillId, userPhone);
                    return new SeckillExecution(seckillId, SeckillStateEnum.SUCCESS, successKilled);
                }
            }
        } catch (SeckillCloseException e1) {
            throw e1;
        } catch (RepeatKillException e2) {
            throw e2;
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            //所有编译器异常转化为运行期异常
            throw new SeckillException("seckill inner error:" + e.getMessage());
        }
    }
```

## web层设计 

- URL设计 采用restful风格url

  `/模块/资源/{标示}/集合1/...`

​       `    GET /seckill/list  秒杀列表   `

​       `  GET /seckill/{id}/detail 详情页 `

​       `GET /seckill/time/now 系统时间 `

​       `POST /seckill/{id}/exposer 暴露秒杀`

​       `POST /seckill/{id}/{md5}/execution 执行秒杀 `

- 获取秒杀列表


```
 @RequestMapping(value = "/list", method = RequestMethod.GET)
    public String getList(Long seckillId, Model model) {
        List<Seckill> list = seckillService.getSeckillList();
        model.addAttribute("list", list);
        System.out.println("asdjk");
        return "list";
    }
```

- 商品详情页

```
 @RequestMapping(value = "/{seckillId}/detail", method = RequestMethod.GET)
    public String detail(@PathVariable("seckillId") Long seckillId, Model model) {
        if (seckillId == null) {
            return "redirect:/seckill/list";
        }
        Seckill seckill = seckillService.getById(seckillId);
        if (seckill == null) {
            return "forward:/seckill/list";
        }
        model.addAttribute("seckill", seckill);
        return "detail";
    }
```

- 暴露秒杀接口地址

```
@RequestMapping(value = "/{seckillId}/exposer", method = RequestMethod.POST)
    @ResponseBody
    public SeckillResult<Exposer> exposer(@PathVariable("seckillId") Long seckillId) {
        SeckillResult<Exposer> result;
        try {
            Exposer exposer = seckillService.exportSeckillUrl(seckillId);
            System.out.println("seckillId:" + seckillId);
            result = new SeckillResult<>(true, exposer);
        } catch (Exception e) {
            e.printStackTrace();
            result = new SeckillResult<>(false, e.getMessage());
        }
        System.out.println("返回结果：" + result.getData());
        return result;
    }
```

- 执行秒杀操作

```
 @RequestMapping(value = "/{seckillId}/{md5}/execution", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public SeckillResult<SeckillExecution> execute(@PathVariable("seckillId") Long seckillId, @PathVariable("md5") String md5, @CookieValue(value = "killPhone", required = false) Long userPhone) {
        SeckillResult<SeckillExecution> result;
        System.out.println("执行秒杀" + md5);
        if (userPhone == null) {
            return new SeckillResult<>(false, "未注册");
        }
        try {
            SeckillExecution execution = seckillService.executeSeckill(seckillId, userPhone, md5);
            return new SeckillResult<>(true, execution);
        } catch (RepeatKillException e) {
            SeckillExecution execution = new SeckillExecution(seckillId, SeckillStateEnum.REPEAT_KILL);
            System.out.println("重复秒杀");
            return new SeckillResult<>(true, execution);
        } catch (SeckillCloseException e) {
            SeckillExecution execution = new SeckillExecution(seckillId, SeckillStateEnum.END);
            System.out.println("秒杀已结束");
            return new SeckillResult<>(true, execution);
        } catch (SeckillException e) {
            SeckillExecution execution = new SeckillExecution(seckillId, SeckillStateEnum.INNER_ERROR);
            System.out.println("内部错误");
            return new SeckillResult<>(true, execution);
        }
    }
```

- 显示当前系统时间

```
 @RequestMapping(value = "/time/now",method = RequestMethod.GET)
    @ResponseBody
    public SeckillResult<Long> time(){
        Date now = new Date();
        System.out.println(new SeckillResult(true,now.getTime()));
        return new SeckillResult(true,now.getTime());
    }
```

- 处理秒杀操作逻辑

```
handleSeckillkill:function(seckillId,node){
        node.hide().html('<button class="btn btn-primary btn-lg" id="killBtn">开始秒杀</button>');
        $.post(seckill.URL.exposer(seckillId),{},function (result) {
            //在回调函数中执行交互流程
            var result =  eval("("+result+")")
            console.log("得到结果"+result['success'] + seckillId)
            if (result && result['success']){
                var exposer = result['data'];
                console.log(exposer)
                if (exposer['exposer']) {
                    //开启秒杀
                    //获取秒杀地址
                    var md5 = exposer['md5'];
                    var killUrl = seckill.URL.execution(seckillId,md5);
                    console.log("killUrl : " + killUrl)
                    //绑定一次点击事件，降低服务器端的压力
                    $('#killBtn').one('click',function () {
                        //执行秒杀执行的操作
                        //1.先禁用按钮
                        $(this).addClass('disable');
                        //2.发送秒杀请求执行秒杀
                        $.post(killUrl,{},function (result) {
                            //var result = eval("("+result+")");
                            console.log('result:'+result);
                            if (result && result['success']){
                                var killResult = result['data'];
                                console.log(killResult);
                                var state = killResult['state'];
                                var stateInfo = killResult['stateInfo'];
                                node.html('<span class="label label-success">' + stateInfo + '</span>')

                            }
                        });
                    });
                    node.show();
                }else {
                    //未开启秒杀
                    var now = exposer['now'];
                    var start = exposer['startTime'];
                    var end = exposer['endTime'];
                    //为防止计时结束，但未开始秒杀的情况，重新计算计时逻辑
                    seckill.countDown(seckillId,now,start,end);
                }
            }else {
                console.log('result:' + result);
            }
        })
    },
```

## 优化

###使用CDN内容分发网络加速用户获取静态数据，将html页面、js函数的等放在CDN上，部署在离用户最近的网络节点上，因此不需要访问后端服务器，减轻后端服务器的压力

### 使用redis后端缓存

未使用redis之前，每一次暴露秒杀地址都需要从数据库进行查询再返回给java客户端，网络延迟和GC会使返回结果时间过长，使用redis之后，每次暴露秒杀地址时先从缓存查看是否存在响应数据，如果有，则直接从缓存中取数据，否则再从数据库查询，最后将查询的结果放到缓存中，方便下次使用，从而缩短查询结果时间。

- 首先需引入相应的依赖：

```
 <!--redis客户端-->
    <dependency>
      <groupId>redis.clients</groupId>
      <artifactId>jedis</artifactId>
      <version>2.9.0</version>
    </dependency>

    <!--redis内部没有实现序列化，因此需要添加自定义序列化依赖-->
    <dependency>
      <groupId>com.dyuproject.protostuff</groupId>
      <artifactId>protostuff-core</artifactId>
      <version>1.1.3</version>
    </dependency>
    <dependency>
      <groupId>com.dyuproject.protostuff</groupId>
      <artifactId>protostuff-runtime</artifactId>
      <version>1.1.3</version>
    </dependency>
```

- 接着设计redis缓存的逻辑

```
public Seckill getSeckill(long seckillId){
        //redis操作逻辑
        try {
            Jedis jedis = jedisPool.getResource();
            try {
                String key = "seckill:"+seckillId;

                //redis内部没有实现序列化操作，因此需要进行自定义序列化
                //get->拿到字节数组bytes[]->进行反序列化—>Object
                byte[] bytes = jedis.get(key.getBytes());
                if (bytes != null){
                    //空对象
                    Seckill seckill = schema.newMessage();
                    //seckill被反序列化
                    ProtobufIOUtil.mergeFrom(bytes,seckill,schema);
                }
            } finally {
                jedis.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    public String putSeckill(Seckill seckill){
        try {
            Jedis jedis = jedisPool.getResource();
            //set Object->序列化->bytes[]
            try {
                String key = "seckill:"+seckill.getSeckillId();
                byte[] bytes = ProtobufIOUtil.toByteArray(seckill,schema,
                        LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE));

                //缓存超时
                int timeout = 60*60;
                String result = jedis.setex(key.getBytes(),timeout,bytes);
                return result;
            } finally {
                jedis.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
```

- 修改SeckillServiceImpl.java

```
  Seckill seckill = redisDao.getSeckill(seckillId);
        if (seckill == null){
            seckill = seckillDao.queryById(seckillId);
            if (seckill == null){
                return new Exposer(false, seckillId);
            }else {
                redisDao.putSeckill(seckill);
            }
        }
```

### 秒杀操作优化

**优化前：**update->insert->commit(中间需经历两次「网络延迟」和「GC」)

**优化后：**insert->update->commit(通过调整插入和更新的顺序，在进行insert操作后判断update是否需要执行（insert返回1，则插入成功，需执行update，否则插入失败，不需执行update操作），从而将延时时间缩小为原来的一半)

```
/* int updateCount = seckillDao.reduceNumber(seckillId, nowTime);
            if (updateCount <= 0) {
                //没有更新到记录
                throw new SeckillCloseException("seckill is closed");
            } else {
                //记录购买行为
                int insertCount = successKilledDao.insertSuccessKilled(seckillId, userPhone);
                if (insertCount <= 0) {
                    //重复秒杀
                    throw new RepeatKillException("seckill repeated");
                } else {
                    SuccessKilled successKilled = successKilledDao.queryByIdWithSeckill(seckillId, userPhone);
                    return new SeckillExecution(seckillId, SeckillStateEnum.SUCCESS, successKilled);
                }
            }*/
            //记录购买行为
            int insertCount = successKilledDao.insertSuccessKilled(seckillId, userPhone);
            if (insertCount <= 0) {
                //重复秒杀
                throw new RepeatKillException("seckill repeated");
            } else {
                int updateCount = seckillDao.reduceNumber(seckillId, nowTime);
                if (updateCount <= 0) {
                    //没有更新到记录
                    throw new SeckillCloseException("seckill is closed");
                } else {
                    SuccessKilled successKilled = successKilledDao.queryByIdWithSeckill(seckillId, userPhone);
                    return new SeckillExecution(seckillId, SeckillStateEnum.SUCCESS, successKilled);
                }
            }
```

#### 深度优化

使用存储过程执行秒杀操作，使用存储过程使所有的数据访问都在服务器内部进行，不需传输局到其他终端，能有效缩短响应时间（之前是每进行一步都需返回到java客户端）。

- 定义存储过程

```
-- 秒杀执行存储过程
DELIMITER $$ -- console ; 转换为 $$
-- 定义存储过程
-- 参数: in 输入参数; out 输出参数
-- row_count():返回上一条修改类型sql(delete,insert,update)的影响行数
-- row_count: 0:未修改数据; >0:表示修改的行数; <0:sql错误/未执行修改sql
CREATE PROCEDURE `seckill`.`execute_seckill`
  (in v_seckill_id bigint,in v_phone bigint,
    in v_kill_time timestamp,out r_result int)
  BEGIN
    DECLARE insert_count int DEFAULT 0;
    START TRANSACTION;
    insert ignore into success_killed
      (seckill_id,user_phone,create_time)
      values (v_seckill_id,v_phone,v_kill_time);
    select row_count() into insert_count;
    IF (insert_count = 0) THEN
      ROLLBACK;
      set r_result = -1;
    ELSEIF(insert_count < 0) THEN
      ROLLBACK;
      SET R_RESULT = -2;
    ELSE
      update seckill
      set number = number-1
      where seckill_id = v_seckill_id
        and end_time > v_kill_time
        and start_time < v_kill_time
        and number > 0;
      select row_count() into insert_count;
      IF (insert_count = 0) THEN
        ROLLBACK;
        set r_result = 0;
      ELSEIF (insert_count < 0) THEN
        ROLLBACK;
        set r_result = -2;
      ELSE
        COMMIT;
        set r_result = 1;
      END IF;
    END IF;
  END;
$$
-- 存储过程定义结束
```

- 在dao层新增`void killByProcedure(Map<String,Object> paramMap);`  接口，修改`SeckillDao.xml`

```
 <!--mybatis调用存储过程-->
    <select id="killByProcedure" statementType="CALLABLE">
        call execute_seckill(
          #{seckillId,jdbcType=BIGINT,mode=IN},
          #{phone,jdbcType=TIMESTAMP,mode=IN},
          #{killTime,jdbcType=BIGINT,mode=IN},
          #{result,jdbcType=INTEGER,mode=OUT}
        )
    </select>
```

- 修改`SeckillServiceImpl.java`  

```
public SeckillExecution executeSeckillProcedure(long seckillId, long userPhone, String md5) {
        if (md5 == null ||!md5.equals(getMd5(seckillId))){
            return new SeckillExecution(seckillId,SeckillStateEnum.DATA_REWRITE);
        }
        Date killTime = new Date();
        Map<String,Object> map = new HashMap<>();
        map.put("seckillId",seckillId);
        map.put("phone",userPhone);
        map.put("killTime",killTime);
        map.put("result",null);
        //执行存储过程，result被赋值
        try {
            seckillDao.killByProcedure(map);
            int result = MapUtils.getInteger(map,"result",-2);
            System.out.println(result);
            if (result == 1){
                SuccessKilled successKilled = successKilledDao
                        .queryByIdWithSeckill(seckillId,userPhone);
                return new SeckillExecution(seckillId,SeckillStateEnum.SUCCESS,successKilled);
            }else {
                return new SeckillExecution(seckillId,SeckillStateEnum.stateOf(result));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new SeckillExecution(seckillId,SeckillStateEnum.INNER_ERROR);
        }
```

- 修改`SeckillController.java`  

```
SeckillExecution execution = seckillService.executeSeckillProcedure(seckillId,userPhone,md5);
```



​        eval本身的问题。 由于json是以”{}”的方式来开始以及结束的，在JS中，它会被当成一个语句块来处理，所以必须强制性的将它转换成一种表达式。
