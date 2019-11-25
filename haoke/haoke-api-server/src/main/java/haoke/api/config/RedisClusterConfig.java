package haoke.api.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class RedisClusterConfig {

    @Autowired
    private ClusterConfigurationProperties clusterProperties;

    @Bean
    public RedisConnectionFactory connectionFactory() {
        RedisClusterConfiguration configuration = new
                RedisClusterConfiguration(clusterProperties.getNodes());
        configuration.setMaxRedirects(clusterProperties.getMaxRedirects());
        return new JedisConnectionFactory(configuration);
    }


    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory){
        RedisTemplate<String, String> redisTemplate = new RedisTemplate<String,String>();
        redisTemplate.setConnectionFactory(factory);

        /**
         * 定义好三种序列化方式：Jackson序列化、Jdk序列化、String序列化
         */

        /**Jackson序列化  json占用的内存最小 */
        Jackson2JsonRedisSerializer jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer(Object.class);
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
        jackson2JsonRedisSerializer.setObjectMapper(om);

        /**Jdk序列化   JdkSerializationRedisSerializer是最高效的*/
        // JdkSerializationRedisSerializer jdkSerializationRedisSerializer = new JdkSerializationRedisSerializer();

        /**String序列化*/
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        /**
         * 设置不同数据类型的序列化方式
         * /

         /**将key value 进行stringRedisSerializer序列化*/
        redisTemplate.setKeySerializer(stringRedisSerializer);
        redisTemplate.setValueSerializer(stringRedisSerializer);

        /**将HashKey HashValue 进行序列化*/
        redisTemplate.setHashKeySerializer(stringRedisSerializer);
        redisTemplate.setHashValueSerializer(jackson2JsonRedisSerializer);

        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    //注入redis中key的生成方式 到时可以在方法的注解上直接调用
    @Bean
    public KeyGenerator simpleKeyGenerator() {
        return (o, method, objects) -> {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(o.getClass().getSimpleName());
            stringBuilder.append(".");
            stringBuilder.append(method.getName());
            stringBuilder.append("[");
            for (Object obj : objects) {
                stringBuilder.append(obj.toString());
            }
            stringBuilder.append("]");

            return stringBuilder.toString();
        };
    }

    //配置CacheManager
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        return new RedisCacheManager(
                RedisCacheWriter.nonLockingRedisCacheWriter(redisConnectionFactory),
                this.getRedisCacheConfigurationWithTtl(180), // 默认策略，未配置的 key 会使用这个
                this.getRedisCacheConfigurationMap() // 指定 key 策略
        );
    }

    //自定缓存策略
    private Map<String, RedisCacheConfiguration> getRedisCacheConfigurationMap() {
        Map<String, RedisCacheConfiguration> redisCacheConfigurationMap = new HashMap<>();
        /**
         *可以通过@Cacheable、@CachPut、@CachEvict注解中的value找到对应方法 然后设置过期时间
         */
        redisCacheConfigurationMap.put("在使用Cach注解的方法的value", this.getRedisCacheConfigurationWithTtl(100));//单位是秒
        redisCacheConfigurationMap.put("在使用Cach注解的方法的value", this.getRedisCacheConfigurationWithTtl(18000));

        return redisCacheConfigurationMap;
    }

    //给自定缓存策略设置序列化方式以及过期时间
    private RedisCacheConfiguration getRedisCacheConfigurationWithTtl(Integer seconds) {
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
        jackson2JsonRedisSerializer.setObjectMapper(om);

        RedisCacheConfiguration redisCacheConfiguration = RedisCacheConfiguration.defaultCacheConfig();
        redisCacheConfiguration = redisCacheConfiguration.serializeValuesWith(
                //设置序列化方式
                RedisSerializationContext
                        .SerializationPair
                        .fromSerializer(jackson2JsonRedisSerializer)
        ).entryTtl(Duration.ofSeconds(seconds));  //设置过期时间

        return redisCacheConfiguration;
    }

}
