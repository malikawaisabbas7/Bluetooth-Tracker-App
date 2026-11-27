FROM node:22-slim
WORKDIR /srv
COPY --chown=node:node server.mjs ./
USER node
CMD ["node", "server.mjs"]
