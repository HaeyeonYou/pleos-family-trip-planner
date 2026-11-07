"""course starts

Revision ID: a3c1d9e4b7f2
Revises: 81149c7661f0
Create Date: 2026-11-08 01:10:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision: str = 'a3c1d9e4b7f2'
down_revision: Union[str, Sequence[str], None] = '81149c7661f0'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    op.create_table('course_starts',
    sa.Column('id', sa.Uuid(), nullable=False),
    sa.Column('place_key', sa.String(length=80), nullable=False),
    sa.Column('started_at', sa.DateTime(timezone=True), nullable=False),
    sa.PrimaryKeyConstraint('id', name=op.f('pk_course_starts'))
    )
    op.create_index('ix_course_starts_place_key_started_at', 'course_starts', ['place_key', 'started_at'], unique=False)


def downgrade() -> None:
    """Downgrade schema."""
    op.drop_index('ix_course_starts_place_key_started_at', table_name='course_starts')
    op.drop_table('course_starts')
