"""실제 API 응답을 파일로 저장해 두고 테스트에서 재사용하는 httpx 전송 계층.

- 기본: 저장된 응답이 있으면 그것을 쓰고, 없으면 실제로 호출해 저장한다.
- API_CACHE=refresh: 항상 실제로 호출해 저장본을 갱신한다 (API 변경 검증).
- API_CACHE=offline: 저장본만 쓰고, 없으면 실패한다.

저장본에는 API 키가 들어가지 않는다 (키 파라미터·인증 헤더는 캐시 키와 파일에서 뺀다).
httpx와, Anthropic SDK가 쓰는 httpx2 둘 다 지원한다 (API가 같음).
"""

import hashlib
import json
import os
from pathlib import Path

import httpx

CACHE_DIR = Path(__file__).parent / "api_cache"
SECRET_PARAMS = {"serviceKey", "authKey"}


def caching_transport(http_module=httpx, cache_dir: Path = CACHE_DIR, mode: str | None = None):
    """http_module(httpx 또는 httpx2)용 캐시 전송 계층을 만든다."""

    class CachingTransport(_CachingMixin, http_module.BaseTransport):
        pass

    return CachingTransport(http_module, cache_dir, mode or os.environ.get("API_CACHE", "auto"))


class _CachingMixin:
    def __init__(self, http_module, cache_dir: Path, mode: str):
        self._http = http_module
        self._dir = cache_dir
        self._mode = mode
        self._real = http_module.HTTPTransport()
        self.real_calls = 0

    def handle_request(self, request):
        path = self._dir / f"{_key(request)}.json"
        if self._mode != "refresh" and path.exists():
            saved = json.loads(path.read_text(encoding="utf-8"))
            return self._http.Response(
                saved["status"],
                headers={"content-type": saved["content_type"]},
                content=saved["body"].encode("utf-8"),
                request=request,
            )
        if self._mode == "offline":
            raise RuntimeError(f"저장된 응답 없음: {_describe(request)} (API_CACHE=offline)")

        response = self._real.handle_request(request)
        response.read()
        self.real_calls += 1
        if response.status_code == 200:
            self._dir.mkdir(parents=True, exist_ok=True)
            path.write_text(
                json.dumps(
                    {
                        "request": _describe(request),
                        "status": response.status_code,
                        "content_type": response.headers.get("content-type", "application/json"),
                        "body": response.text,
                    },
                    ensure_ascii=False,
                    indent=1,
                ),
                encoding="utf-8",
            )
        return response


def _describe(request) -> str:
    params = sorted((k, v) for k, v in request.url.params.multi_items() if k not in SECRET_PARAMS)
    query = "&".join(f"{k}={v}" for k, v in params)
    return f"{request.method} {request.url.host}{request.url.path}?{query}"


def _key(request) -> str:
    body = request.content.decode("utf-8", "replace") if request.content else ""
    digest = hashlib.sha1(f"{_describe(request)}\n{body}".encode()).hexdigest()[:16]
    return f"{request.url.host.split('.')[0]}_{request.url.path.strip('/').replace('/', '_')}_{digest}"
