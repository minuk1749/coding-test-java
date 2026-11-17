# 코딩테스트 학습 기록 자동화

코드트리의 GitHub 제출 기록을 읽어 Notion의 **문제 하나 → 1차·2차 시도**로 보관합니다. 매 시도의 코드는 그 커밋에서 읽고, 코멘트는 사용자가 직접 작성합니다. 기존 페이지를 수정·삭제하지 않습니다.

## 사용 흐름

1. 코드트리에 제출하면 연결된 저장소에 커밋이 생깁니다.
2. `main`의 push가 GitHub Actions를 실행합니다.
3. Java 프로그램이 전체 커밋 이력을 부모 순서로 읽고 문제별 시도 번호를 붙입니다.
4. Notion에 없는 문제와 시도만 생성합니다. 문제의 `시도 이력`에서 각 시도를 열 수 있습니다.
5. 사용자는 시도 페이지의 `내 코멘트`, 문제 페이지의 `문제 전체 정리`에 메모합니다.

이 프로그램은 제출 코드를 실행하거나 정답을 판단하지 않습니다. GitHub에 기록된 판정만 옮깁니다. LLM 호출과 자동 해설은 현재 버전에 없습니다.

## 최초 연결

기록 대상: [코딩테스트](https://app.notion.com/p/41bda39282f1802bb138c9a48633354e)

1. Notion의 Developer portal에서 이 자동화용 내부 연결을 만들고, 콘텐츠 읽기·추가 권한을 허용합니다. `코딩테스트` 페이지에 해당 연결을 추가하여 두 데이터 소스에 접근할 수 있게 합니다. 개인용 PAT도 API에서 지원하지만, 페이지 접근을 제한하기 쉬운 내부 연결을 권장합니다.
2. GitHub 저장소의 **Settings → Secrets and variables → Actions → New repository secret**에 `NOTION_TOKEN`이라는 이름으로 토큰을 저장합니다. 채팅·소스·README에는 토큰을 넣지 않습니다.
3. **Actions → Notion study sync → Run workflow**에서 `dry_run`을 해제하고 실행합니다. 첫 실행은 GitHub에 남아 있는 과거 제출도 가져옵니다.
4. 동일한 workflow를 다시 실행하여 새 시도 0개인지 확인합니다. 노션 메모를 작성한 뒤 재실행해 메모가 남는지도 확인합니다.
5. 이후 실제 코드트리 제출 한 건으로 push 트리거와 저장까지 확인합니다. 커밋 인증 방식에 따라 push 후 실행이 생기지 않을 수 있으므로 이 검증은 필요합니다.

토큰이 없으면 검사·미리보기만 수행하며 자동 기록은 꺼져 있다는 안내를 Actions summary에 남깁니다. ChatGPT의 Notion 연결과 GitHub Actions가 사용할 토큰은 별개입니다.

다른 노션 대상으로 옮길 때 Repository variables를 설정하면 됩니다.

| 이름 | 현재 기본 데이터 소스 |
|---|---|
| `NOTION_PROBLEMS_DATA_SOURCE_ID` | `41bda392-82f1-8130-9edd-000b3999b05b` |
| `NOTION_ATTEMPTS_DATA_SOURCE_ID` | `41bda392-82f1-819c-b4d6-000b0f4efcf8` |

데이터베이스 페이지 ID와 데이터 소스 ID는 다릅니다. 두 ID는 비밀 토큰이 아니며 접근 권한을 대신하지 않습니다.

## 설계 결정

| 항목 | 규칙 |
|---|---|
| 문제 식별 | `codetree:` + 문제 URL의 마지막 경로. 폴더명·표시 제목과 분리 |
| 시도 식별 | 저장소 + 문제 키 + 전체 커밋 SHA |
| 차수 | Git 부모 순서로 문제별 1, 2, 3… 부여. 날짜 역전에도 순서 유지 |
| 동일 코드 재제출 | 별도 제출 커밋이 있다면 별도 시도로 저장 |
| 재실행 | 같은 시도 키가 있으면 본문을 포함해 그대로 보존 |
| 코드가 없는 이론 문제 | 코드 없음으로 기록. 답안이나 오답 이유를 만들어내지 않음 |
| 시간 | 실제 제출 시각을 추측하지 않고 Git 커밋의 기록 시각 사용 |
| 상태 저장 | Notion 데이터 소스에 동기화 키를 저장. 일회성 Actions 캐시에 의존하지 않음 |
| 일부 실패 | 이미 생성된 기록을 남기고 다음 실행에서 빠진 기록 처리 |
| 불확실한 쓰기 응답 | 키로 재조회. 확인되지 않으면 중단하며 같은 POST를 즉시 반복하지 않음 |
| 동시 실행 | GitHub Actions concurrency로 단일 쓰기 실행. 별도 로컬 쓰기를 동시에 실행하지 않음 |

### 한계

- **차수는 GitHub에 남아 있는 제출 기준**입니다. 코드트리가 GitHub에 보내지 않은 시도는 복원할 수 없습니다. 실제 사이트의 총 시도 횟수와 일치한다고 보장하지 않습니다.
- 지원 입력은 코드트리의 `[Passed]`, `[Wrong Answer]` 등 판정 커밋과 문제 README입니다. 일반 문서·코드 편집은 시도로 세지 않습니다. 프로그래머스 파서는 다음 단계입니다.
- `main`의 first-parent 이력이 기준입니다. 이력 재작성·다른 브랜치의 제출을 합치는 방식은 지원하지 않습니다. 기존 노션 기록과 이력이 어긋나면 자동 번호 변경 대신 중단합니다.
- 생성한 시도를 삭제하면 다음 실행에서 다시 생성됩니다. 보존하고 싶다면 삭제 대신 메모로 표시하세요.
- 자동화용 `문제 키`, `시도 키`, `차수`는 변경하지 마세요. 기본 뷰에서는 키를 숨겼습니다.
- Notion API는 고유 제약/분산 트랜잭션을 제공하는 DB가 아니므로 엄격한 exactly-once 보장은 아닙니다. 단일 실행, 저장된 키, 오류 후 재조회로 중복 위험을 줄입니다. 여러 프로세스의 동시 수동 실행은 지원하지 않습니다.
- 매우 큰 코드는 Notion 요청 제한을 넘기면 일부를 잘라 저장하지 않고 명시적으로 실패합니다.

## 실행과 검증

필요 환경: Java 21, Git. Maven Wrapper가 Maven을 내려받습니다. 저장소 최상위에서 실행합니다.

```sh
./notion-sync/mvnw -B -f notion-sync/pom.xml verify
java -jar notion-sync/target/algo-study-sync.jar --dry-run --output notion-sync/target/preview.json
```

실제 저장에는 `NOTION_TOKEN`, `NOTION_PROBLEMS_DATA_SOURCE_ID`, `NOTION_ATTEMPTS_DATA_SOURCE_ID`, `GITHUB_REPOSITORY` 환경변수가 필요합니다. dry-run에는 인증이 필요하지 않습니다.

테스트는 임시 Git 저장소와 가짜 Notion 응답으로 과거 코드 보존, 재제출, 중복 방지, 메모 보존, 쓰기 응답 유실, API 제한과 페이지네이션을 확인합니다. 실제 인증·권한·노션 렌더링은 최초 연결 후 별도로 확인해야 합니다.

## 다음 단계: 변경사항 요약

사용자 요청: AI가 이전 시도와 현재 시도의 코드를 비교하여 **확인되는 변경사항만** 작성합니다. 해설, 정답 제안, 사용자의 의도 추측은 포함하지 않습니다.

확장 시 입력은 동일 문제의 인접한 두 시도 스냅샷, 출력은 별도의 `변경사항 요약` 영역입니다. 기존 `내 코멘트`와 분리하고, 모델·프롬프트 버전과 대상 시도 키를 기록합니다. 이는 우선 고정된 AI 워크플로로 구현할 수 있으며, 에이전트 구조를 요구하지 않습니다.

## 근거 문서

- https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow
- https://developers.notion.com/guides/data-apis/working-with-page-content
- https://developers.notion.com/reference/query-a-data-source
- https://developers.notion.com/reference/request-limits
