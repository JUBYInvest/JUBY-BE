package juby.invest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 전체 스프링 컨텍스트가 뜨는지 확인한다.
 * <p>
 * 빈 주입 누락, @Value 프로퍼티 오타, 순환 참조처럼 다른 테스트가 잡지 못하는
 * 배선 문제는 여기서만 드러난다.
 * <p>
 * test 프로필을 쓰는 이유: 기본 프로필(local)은 로컬 MySQL과 .env를 요구해서
 * 인프라가 없으면 이 테스트가 항상 실패한다. 그러면 빌드가 상시 빨간 상태가 되고,
 * 진짜 배선 오류가 섞여 들어와도 아무도 알아채지 못한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("스프링 컨텍스트 로딩")
class InvestApplicationTests {

	@Test
	@DisplayName("모든 빈이 정상적으로 조립된다")
	void contextLoads() {
	}

}
