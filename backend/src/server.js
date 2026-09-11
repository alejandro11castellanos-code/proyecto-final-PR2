import 'dotenv/config';
import { createApp } from './app.js';

const port = process.env.PORT ?? 3000;

if (!process.env.JWT_SECRET) {
  throw new Error('JWT_SECRET es obligatorio para iniciar el servidor.');
}

createApp().listen(port, () => {
  console.log(`SVB-GUA backend en http://localhost:${port}`);
});
