"""child birth_date, rear_facing_seat and care places (onboarding O01~O06, #184)

Revision ID: b7e2c4a91d03
Revises: f0e62ffdd9f8
Create Date: 2026-11-11 15:00:00

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

# revision identifiers, used by Alembic.
revision: str = 'b7e2c4a91d03'
down_revision: Union[str, Sequence[str], None] = 'f0e62ffdd9f8'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    op.add_column('children', sa.Column('birth_date', sa.Date(), nullable=True))
    op.add_column('children', sa.Column('rear_facing_seat', sa.Boolean(), nullable=True))
    op.create_table(
        'care_places',
        sa.Column('id', sa.Uuid(), nullable=False),
        sa.Column('user_id', sa.Uuid(), nullable=False),
        sa.Column('place_key', sa.String(length=80), nullable=False),
        sa.Column('place_name', sa.String(length=60), nullable=False),
        sa.Column('amenities', postgresql.JSONB(astext_type=sa.Text()), server_default='[]', nullable=False),
        sa.Column('visit_count', sa.Integer(), nullable=False),
        sa.Column('last_visited_on', sa.Date(), nullable=True),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(['user_id'], ['users.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('user_id', 'place_name', name='uq_care_places_user_id_place_name'),
    )
    op.create_index(op.f('ix_care_places_user_id'), 'care_places', ['user_id'], unique=False)


def downgrade() -> None:
    """Downgrade schema."""
    op.drop_index(op.f('ix_care_places_user_id'), table_name='care_places')
    op.drop_table('care_places')
    op.drop_column('children', 'rear_facing_seat')
    op.drop_column('children', 'birth_date')
