package spring.common.config;

import com.p6spy.engine.spy.P6DataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

/**
 * 어떤 DataSource가 만들어지든 p6spy로 감싸 SQL과 바인딩된 파라미터를 로그로 남긴다.
 *
 * <p>URL에 {@code jdbc:p6spy:} 접두사를 붙이는 방식을 쓰지 않는 이유: docker-compose·
 * Testcontainers 지원이 실행 중인 컨테이너에서 JdbcConnectionDetails를 만들어
 * {@code spring.datasource.*}를 통째로 덮어쓴다. 그래서 URL에 걸면 조용히 무시된다
 * (실제로 그렇게 한 번 놓쳤다). DataSource를 감싸면 URL 출처와 무관하게 걸린다.
 *
 * <p>출력 포맷·필터는 classpath의 spy.properties가 정한다.
 */
@Configuration
public class P6SpyConfiguration implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        return bean instanceof DataSource dataSource ? new P6DataSource(dataSource) : bean;
    }
}
