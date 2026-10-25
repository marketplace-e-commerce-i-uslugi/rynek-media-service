package pl.rynek.media;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfig.class)
class MediaServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
