FROM python:3.13-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY alembic.ini ./
COPY migrations ./migrations
COPY scripts ./scripts
COPY seed ./seed
COPY app ./app

EXPOSE 8000
# 시작할 때마다 DB를 최신 구조로 맞춘 뒤 서버를 띄운다 (이미 최신이면 아무것도 안 한다)
CMD ["sh", "-c", "alembic upgrade head && exec uvicorn app.main:app --host 0.0.0.0 --port 8000 --no-access-log --proxy-headers"]
