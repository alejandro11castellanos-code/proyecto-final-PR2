import jwt from 'jsonwebtoken';

const unauthorized = { error: 'Autenticación requerida.' };

export function authenticateToken(jwtSecret) {
  return (req, res, next) => {
    const authorization = req.get('Authorization');
    const [scheme, token] = authorization?.split(' ') ?? [];

    if (scheme !== 'Bearer' || !token || !jwtSecret) {
      return res.status(401).json(unauthorized);
    }

    try {
      req.user = jwt.verify(token, jwtSecret);
      return next();
    } catch {
      return res.status(401).json(unauthorized);
    }
  };
}

export function authorizeRoles(...allowedRoles) {
  return (req, res, next) => {
    if (!req.user || !allowedRoles.includes(req.user.rol)) {
      return res.status(403).json({
        error: 'No tiene permisos para realizar esta acción.',
      });
    }
    return next();
  };
}
