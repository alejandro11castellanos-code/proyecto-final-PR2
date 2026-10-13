import 'dotenv/config';
import pg from 'pg';
import { OID_TIMESTAMP, aFechaLocalIso } from './timestamps.js';

const { Pool } = pg;

pg.types.setTypeParser(OID_TIMESTAMP, aFechaLocalIso);

/**
 * Shared connection pool. Managed Postgres (Neon/Supabase) requires SSL;
 * set DATABASE_SSL=false only for a local instance.
 */
export const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: process.env.DATABASE_SSL === 'false' ? false : { rejectUnauthorized: false },
});
