"""visit record role (식사·카페 방문도 경험 젤리로, #234)

Revision ID: d9a4f2b71c34
Revises: a6c2e9f41d58
Create Date: 2026-11-12 12:00:00

차 앱이 나들이뿐 아니라 식사·카페 방문도 올린다. 젤리 12분류 판정에서 식사·카페는 맛 탐험 쪽으로 본다(app/course/jelly.py judge).
예전 기록은 모두 나들이였으므로 'outing'으로 채운다.
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = 'd9a4f2b71c34'
down_revision: Union[str, Sequence[str], None] = 'a6c2e9f41d58'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column('visit_records', sa.Column('role', sa.String(length=8), server_default='outing', nullable=False))


def downgrade() -> None:
    op.drop_column('visit_records', 'role')
