"""visit record category (젤리 12분류 판정 근거, #186)

Revision ID: b7d2e186c0a1
Revises: f0e62ffdd9f8
Create Date: 2026-11-11 12:00:00

예전 기록은 빈 값으로 두고, 젤리 12분류는 조회할 때 이름·분야로 정한다(app/visits.py jelly_of).
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = 'b7d2e186c0a1'
down_revision: Union[str, Sequence[str], None] = 'f0e62ffdd9f8'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column('visit_records', sa.Column('category', sa.String(length=120), server_default='', nullable=False))


def downgrade() -> None:
    op.drop_column('visit_records', 'category')
