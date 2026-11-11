"""climate presets per child (케어 모드 값 아이별, QO5 #192)

Revision ID: c4f1a8e2d6b9
Revises: b7d2e186c0a1
Create Date: 2026-11-11 18:00:00

프리셋에 child_id를 넣는다. 예전 계정 단위 행은 child_id 없이 '가족 기본값'으로 남겨(아이를 모르는 예전 차 앱 호환)
그 값을 지금 있는 아이마다 복사해 아이별 값으로 시작한다(보낸 시각·적용 상태도 그대로).
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = 'c4f1a8e2d6b9'
down_revision: Union[str, Sequence[str], None] = 'b7d2e186c0a1'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column('climate_presets', sa.Column('child_id', sa.Uuid(), nullable=True))
    op.create_foreign_key(op.f('fk_climate_presets_child_id_children'), 'climate_presets', 'children', ['child_id'], ['id'], ondelete='CASCADE')
    op.create_index(op.f('ix_climate_presets_child_id'), 'climate_presets', ['child_id'], unique=False)
    op.drop_constraint(op.f('uq_climate_presets_user_id'), 'climate_presets', type_='unique')
    op.create_unique_constraint(
        'uq_climate_presets_user_child_slot', 'climate_presets', ['user_id', 'child_id', 'slot'], postgresql_nulls_not_distinct=True
    )
    op.execute(
        """
        INSERT INTO climate_presets (id, user_id, child_id, slot, settings, sent_at, applied_at)
        SELECT gen_random_uuid(), p.user_id, c.id, p.slot, p.settings, p.sent_at, p.applied_at
        FROM climate_presets p JOIN children c ON c.user_id = p.user_id
        WHERE p.child_id IS NULL
        """
    )


def downgrade() -> None:
    op.execute("DELETE FROM climate_presets WHERE child_id IS NOT NULL")
    op.drop_constraint('uq_climate_presets_user_child_slot', 'climate_presets', type_='unique')
    op.create_unique_constraint(op.f('uq_climate_presets_user_id'), 'climate_presets', ['user_id', 'slot'])
    op.drop_index(op.f('ix_climate_presets_child_id'), table_name='climate_presets')
    op.drop_constraint(op.f('fk_climate_presets_child_id_children'), 'climate_presets', type_='foreignkey')
    op.drop_column('climate_presets', 'child_id')
