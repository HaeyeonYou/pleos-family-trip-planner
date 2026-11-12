"""운영(NAS) 배포 설정: 주소만 연 사람은 폰 웹으로, 운영에서는 API 문서를 숨긴다."""

import os
import subprocess
import sys
from pathlib import Path

from tests.test_auth import run

SERVER_DIR = Path(__file__).resolve().parents[1]


def test_root_redirects_to_mobile_web():
    async def scenario(client):
        res = await client.get("/")
        assert res.status_code == 307
        assert res.headers["location"] == "/m/"

    run(scenario)


def _docs_paths(expose: str) -> str:
    code = (
        "from app.main import app;"
        "print(app.docs_url, app.redoc_url, app.openapi_url)"
    )
    env = {**os.environ, "EXPOSE_DOCS": expose}
    return subprocess.run([sys.executable, "-c", code], cwd=SERVER_DIR, env=env, capture_output=True, text=True, check=True).stdout.strip()


def test_docs_can_be_hidden_in_production():
    assert _docs_paths("false") == "None None None"
    assert _docs_paths("true") == "/docs /redoc /openapi.json"
