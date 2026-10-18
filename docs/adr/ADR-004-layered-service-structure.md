# ADR-004 Listener-Service-Repository 레이어 구조 사용

## 상태

승인됨

## 배경

Paper 플러그인은 이벤트 리스너에 로직이 몰리기 쉽습니다.  
Mayuela는 제단, 활성화율, 스코어보드, Display, 코인, 인벤토리 잠금 등 책임이 많기 때문에 구조를 나누지 않으면 유지보수가 어려워집니다.

## 선택지

- A안: Listener와 Command에서 모든 로직 직접 처리
- B안: Listener/Command는 입력만 받고 Service로 위임
- C안: 복잡한 DI 프레임워크를 도입

## 결정

Listener/Command -> Service -> Repository/Domain 구조를 사용합니다.

## 이유

- SOLID 원칙 중 단일 책임 원칙을 지키기 쉽습니다.
- 1인 개발에서도 클래스 위치를 예측하기 쉽습니다.
- DI 프레임워크 없이도 생성자 주입으로 충분히 관리할 수 있습니다.

## 결과

- `listener` 패키지는 Paper 이벤트 수신과 취소 처리에 집중합니다.
- `service` 패키지는 게임 규칙과 유스케이스를 처리합니다.
- `repository` 패키지는 YAML 저장과 로드를 담당합니다.
- `model` 패키지는 런타임 도메인 상태를 표현합니다.
