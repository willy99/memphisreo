-- Deal: авто на Lead.WON чи явна дія агента — docs/domain-model.md §6.
ALTER TABLE control_plane.tenant
    ADD COLUMN auto_create_deal_on_won boolean NOT NULL DEFAULT false;
