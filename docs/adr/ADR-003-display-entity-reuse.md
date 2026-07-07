# ADR-003 Display 엔티티 재사용

## 상태

승인됨

## 배경

Mayuela는 플레이어 머리 위 `ItemDisplay`와 제단 위 `TextDisplay`를 사용합니다.  
Display 엔티티를 매번 제거하고 다시 생성하면 엔티티 생성 비용과 정리 누락 위험이 커집니다.

## 선택지

- A안: 갱신 때마다 Display 엔티티를 제거하고 재생성
- B안: Display 엔티티를 생성한 뒤 위치와 텍스트만 갱신

## 결정

Display 엔티티는 재사용합니다.

## 이유

- TPS 퍼포먼스를 보호할 수 있습니다.
- 엔티티 누적 버그 가능성을 줄입니다.
- `DisplayService`가 표시 엔티티의 생명주기를 한 곳에서 관리할 수 있습니다.

## 결과

- 플레이어 머리 위 `ItemDisplay`는 follow task에서 teleport만 수행합니다.
- 제단 위 `TextDisplay`는 점령자 또는 활성화율이 바뀔 때 텍스트를 갱신합니다.
- 게임 종료, 퇴장, 강제 종료 시 `DisplayService.removeAll()` 또는 개별 제거를 호출합니다.
