"""climate preset apply_at (케어 모드 적용 시점, C08 #168)

Revision ID: e2a7c9d14b38
Revises: c4f1a8e2d6b9
Create Date: 2026-11-11 21:00:00

프리셋마다 적용 시점(boarding 탑승하면 바로 / departure 출발 시각 / manual 직접 실행)을 둔다.
비어 있으면 슬롯 기본값(탑승 준비=boarding, 낮잠·수유 정차=manual)이라 기존 행은 그대로 둔다.
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = 'e2a7c9d14b38'
down_revision: Union[str, Sequence[str], None] = 'c4f1a8e2d6b9'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column('climate_presets', sa.Column('apply_at', sa.String(length=12), nullable=True))


def downgrade() -> None:
    op.drop_column('climate_presets', 'apply_at')
