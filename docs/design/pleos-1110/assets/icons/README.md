# 11/10 확정 디자인 아이콘 원본 SVG

Figma 파일 `xeLwkzhH8tnsazQdOriGU2`, 페이지 `종합 정리`(130:5)의 `icon/...` FRAME을 `exportAsync({format: 'SVG_STRING'})`으로 내려받은 원본이다. 기존 `icon-*.svg` 17개는 옛 디자인(FigmaIcons.kt)에서 옮긴 것이라 모두 지우고 이 원본으로 대체했다. 색은 SVG 안의 stroke 값을 그대로 옮겼고, `white`는 Figma 원본이 hex가 아니라 `white`로 적혀 있는 값이다. 화면 목록은 작성 요청에 적힌 대응을 그대로 옮긴 것이며, 각 화면에서 실제로 쓰였는지는 직접 대조하지 않았다(미확인).

## 아이콘 목록

| 파일 | Figma 이름 | 노드 id | 원본 크기 | 선 색 (hex) | 용도 추정 | 등장 화면 |
|---|---|---|---|---|---|---|
| icon-alert.svg | alert | 355:3515 | 30 | #E07A00 | 경고 (느낌표 삼각형) | C01·C05·C06·C08~C11 |
| icon-baby.svg | baby | 361:5154 | 26 | white | 아기 얼굴 | H01·G01·C01~C12·D01 |
| icon-bookmark.svg | bookmark | 353:3064 | 24 | #191F28 | 저장 (북마크) | H02 |
| icon-check.svg | check | 353:3404 | 24 | white | 체크 | H01·H02·H02-1·C03·C04·C09~C11 |
| icon-cl.svg | cl | 353:2894 | 28 | #191F28 | 왼쪽 꺾쇠 (이전) | H02 |
| icon-clock.svg | clock | 353:3041 | 28 | #4D55F0 | 시계 (시간) | H02 |
| icon-cr.svg | cr | 353:3052 | 24 | #8B95A1 | 오른쪽 꺾쇠 (다음·이동) | H02 |
| icon-grip.svg | grip | 361:5285;361:5082 | 26 | #4E5968 | 두 줄 (드래그 손잡이, 추정) | H02·H02-1 |
| icon-heart.svg | heart | 352:3008 | 28 | #4E5968 | 하트 (찜·좋아요, 추정) | H01·G01·C01~C12·D01 |
| icon-home.svg | home | 352:3004 | 28 | #4D55F0 | 홈 | H01·G01·C01~C12·D01 |
| icon-info.svg | info | 353:3047 | 24 | #4E5968 | 정보 (i) | H02 |
| icon-mic.svg | mic | 368:5728;368:5724 | 24 | white | 마이크 (음성 호출) | H01·G01·C01~C12·D01 |
| icon-milk.svg | milk | 355:3423 | 26 | #4E5968 | 젖병 (수유) | C 화면들 |
| icon-minus.svg | minus | 361:5318;361:5074;361:5006 | 16 | #8B95A1 | 빼기 (수량 감소) | H02·H02-1 |
| icon-moon.svg | moon | 355:3411 | 26 | #4E5968 | 달 (수면·야간) | C 화면들 |
| icon-nav.svg | nav | 353:3056 | 24 | white | 길 안내 (내비게이션) | H02 |
| icon-park.svg | park | 366:8739 | 24 | #4D55F0 | 주차 (P) | C 화면들 |
| icon-pencil.svg | pencil | 353:3060 | 24 | #191F28 | 편집 (연필) | H02 |
| icon-plus.svg | plus | 361:5162 | 26 | #4E5968 | 더하기 (추가) | H02·H02-1 |
| icon-sliders.svg | sliders | 352:3017 | 28 | #4E5968 | 슬라이더 (설정·필터, 추정) | H01·G01·C01~C12·D01 |
| icon-sprout.svg | sprout | 352:3011 | 28 | #4E5968 | 새싹 (경험·성장, 추정) | H01·G01·C01~C12·D01 |
| icon-star.svg | star | 355:3464 | 22 | #4D55F0 | 별 (즐겨찾기·평점, 추정) | C 화면들 |
| icon-store.svg | store | 352:2974 | 40 | #4E5968 | 상점 (건물·매장, 추정) | H01 |
| icon-sun.svg | sun | 352:2767 | 34 | #F59E0B | 해 (낮·날씨, 추정) | H01·G01·C01~C12·D01 |
| icon-timer.svg | timer | 355:3443 | 26 | #E07A00 | 타이머 (수유 타이머) | C 화면들 |
| icon-trees.svg | trees | 352:2992 | 40 | #4E5968 | 나무 (경험 나무, 추정) | H01 |
| icon-turn.svg | turn | 352:2764 | 30 | white | 꺾인 화살표 (전환·교체, 미확인) | H01·G01·C01~C12·D01 |
| icon-waves.svg | waves | 352:2983 | 40 | #4E5968 | 물결 (바다·강, 추정) | H01 |
| icon-x.svg | x | 361:5285;361:5079 | 26 | #4E5968 | 닫기 (X) | H02·H02-1 |

참고: `park`의 화면은 "C 화면들"로만 적혀 있어 어느 C 화면인지 미확인이다. `milk`·`moon`·`timer`·`star`·`park`도 같다. 용도 칸의 "추정"은 이름과 모양으로 짐작한 것이고, 확신이 없는 것은 "미확인"이라고 적었다. `turn`의 용도는 모양만 보고 판단할 수 없어 미확인이다.

## 앱(Android Compose)에서 쓰는 법

앱은 `android/app/src/main/java/io/github/haeyeonyou/familytrip/ui/FigmaIcons.kt`에 SVG path를 Compose `ImageVector`로 옮겨 써 왔다. 새 원본을 쓰려면 이 SVG의 `<path d="...">`와 stroke 색, stroke-width, 원본 크기(viewBox)를 같은 방식으로 옮기면 된다. 이미 FigmaIcons.kt에 있는 이름(parking, baby, clock, sliders, shield, minus, plus, pin, sun, paw, check, bag, nav, mic, milk, sprout, chev, alert, timer, star)은 옛 디자인에서 옮긴 것이라 11/10 원본과 모양이나 색이 다를 수 있다. 교체하기 전에 이 폴더의 SVG와 나란히 비교한 뒤 교체 여부를 정해야 한다. 아직 비교하지 않았다(미확인).

## 저장하지 못한 것

없음. 요청한 29개 노드 모두 SVG 문자열을 받았고, 29개 파일로 저장했다. `<svg`로 시작하는 파일이며 0바이트가 없고, md5 중복도 없다.
