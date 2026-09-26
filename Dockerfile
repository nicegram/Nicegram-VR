FROM node:24-alpine
WORKDIR /app
COPY --chown=node:node package.json server.mjs ./
USER node
ENV PORT=8080
EXPOSE 8080
CMD ["node", "server.mjs"]
