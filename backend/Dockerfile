# ---- build ----
FROM node:24-alpine AS build
WORKDIR /app
RUN apk add --no-cache openssl
COPY package*.json ./
RUN npm ci --no-audit --no-fund
COPY prisma ./prisma
RUN npx prisma generate
COPY tsconfig*.json nest-cli.json ./
COPY src ./src
RUN npm run build && npm prune --omit=dev

# ---- runtime ----
FROM node:24-alpine
WORKDIR /app
RUN apk add --no-cache openssl
ENV NODE_ENV=production
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/dist ./dist
COPY --from=build /app/prisma ./prisma
COPY package.json ./
USER node
EXPOSE 3000
# Apply pending migrations, then start. Demo accounts (known passwords!) are only seeded when
# SEED_DEMO_DATA=true, which docker-compose.yml sets for local development.
CMD ["sh", "-c", "npx prisma migrate deploy && if [ x$SEED_DEMO_DATA = xtrue ]; then node prisma/seed.js; fi && node dist/main.js"]
