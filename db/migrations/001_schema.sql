CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE travel_plans (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(200) NOT NULL,
    description TEXT,
    start_date DATE,
    end_date DATE,
    budget DECIMAL(10, 2),
    currency VARCHAR(3) DEFAULT 'USD',
    is_public BOOLEAN DEFAULT FALSE,
    version INTEGER,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
    );

CREATE TABLE locations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    travel_plan_id uuid NOT NULL,
    name VARCHAR(200) NOT NULL,
    address TEXT,
    latitude DECIMAL(10, 6),
    longitude DECIMAL(11, 6),
    visit_order INTEGER NOT NULL,
    arrival_date TIMESTAMPTZ,
    departure_date TIMESTAMPTZ,
    budget DECIMAL(10, 2),
    notes TEXT,
    version INTEGER,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT fk_travel_plan FOREIGN KEY(travel_plan_id) REFERENCES travel_plans(id) ON DELETE CASCADE
    );