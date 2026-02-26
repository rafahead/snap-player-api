# HOWTO — Deploy manual em Linode (Ubuntu) "na unha"

Guia prático para subir o `snap-player-api` em uma VM Ubuntu na Linode,
sem Docker, usando PostgreSQL + Nginx + `systemd`.

Este HOWTO segue a Entrega 6 (deploy atual). Docker fica como trilha futura.

---

## 1. Arquitetura alvo (produção atual)

- VM Ubuntu (Linode)
- PostgreSQL instalado na própria VM
- Nginx como reverse proxy
- App Spring Boot rodando como serviço `systemd`
- FFmpeg/FFprobe no host
- Storage de artefatos em Linode Object Storage (S3 compatível)

Fluxo:

`Internet -> Nginx (80/443) -> app (127.0.0.1:8080) -> PostgreSQL + S3`

---

## 2. Pré-requisitos

- Conta Linode + domínio/subdomínio (ex.: `api.seudominio.com`)
- Acesso SSH à VM
- Bucket no Linode Object Storage + credenciais
- Java 17 e Maven instalados localmente (para gerar o `.jar`)

Arquivos de apoio já versionados no projeto:

- `src/main/resources/application-prod.yml`
- `deploy/ubuntu/systemd/snap-player-api.service`
- `deploy/ubuntu/nginx/snap-player-api.conf`
- `deploy/ubuntu/env/snap-player-api.env.example`

---

## 3. Criar a VM na Linode (manual)

Sugestão inicial:

- Ubuntu LTS
- Shared CPU (plano compatível com sua carga; 2GB funciona como base)
- Região próxima dos usuários
- SSH Key cadastrada na criação

Depois de criar:

- Aponte DNS (`A record`) para o IP público da VM

---

## 4. Acesso inicial e endurecimento básico

Conecte por SSH como `root` (primeiro acesso), crie um usuário admin e use esse usuário no dia a dia.

Checklist recomendado:

- Criar usuário admin com sudo
- Desabilitar login por senha (usar chave SSH)
- Atualizar pacotes
- Configurar firewall (`ufw`) liberando `22`, `80`, `443`
- Ajustar timezone/locale se necessário

---

## 5. Instalar dependências no Ubuntu

Instale:

- `openjdk-17-jre-headless`
- `ffmpeg`
- `fonts-dejavu-core`
- `postgresql`
- `nginx`

Valide no host:

- `java -version`
- `ffmpeg -version`
- `ffprobe -version`
- `systemctl status postgresql`
- `systemctl status nginx`

---

## 6. Configurar PostgreSQL (local da VM)

Crie banco e usuário da aplicação:

- Banco: `snap_player` (ou nome de sua preferência)
- Usuário: `snapplayer`
- Senha forte

Permissões mínimas:

- Dono do banco para o usuário da app (ou grants equivalentes)

Observação:

- O Spring/Flyway cria/atualiza schema automaticamente ao subir a aplicação.

---

## 7. Gerar o artefato da aplicação (.jar)

No seu ambiente local (ou CI), gere o build:

- `mvn -Dmaven.repo.local=.m2/repository package`

Artefato esperado:

- `target/snap-player-api-0.0.1-SNAPSHOT.jar`

---

## 8. Preparar diretórios e usuário de runtime na VM

Crie um usuário de serviço (sem shell) para rodar a aplicação:

- usuário/grupo: `snapplayer`

Crie diretórios:

- `/opt/snap-player-api` (app + jar)
- `/etc/snap-player-api` (env file)
- `/data/tmp/video-frames-processing` (temp do processamento)

Permissões:

- Dono `snapplayer:snapplayer` em `/opt/snap-player-api` e `/data/tmp/video-frames-processing`

---

## 9. Copiar arquivos para a VM

Copie:

- `.jar` para `/opt/snap-player-api/snap-player-api.jar`
- `deploy/ubuntu/systemd/snap-player-api.service` para `/etc/systemd/system/`
- `deploy/ubuntu/nginx/snap-player-api.conf` para `/etc/nginx/sites-available/`
- `deploy/ubuntu/env/snap-player-api.env.example` para `/etc/snap-player-api/snap-player-api.env`

Depois, habilite o site no Nginx:

- symlink em `/etc/nginx/sites-enabled/`

---

## 10. Configurar variáveis de ambiente (produção)

Edite `/etc/snap-player-api/snap-player-api.env`.

Campos principais:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `STORAGE_ENDPOINT`
- `STORAGE_REGION`
- `STORAGE_BUCKET`
- `STORAGE_ACCESS_KEY`
- `STORAGE_SECRET_KEY`
- `STORAGE_PUBLIC_BASE_URL`
- `STORAGE_PREFIX` (opcional)
- `SNAP_PUBLIC_BASE_URL` (URL pública da API)
- `APP_INTERNAL_ACCESS_TOKEN` (recomendado)

Exemplo de `DB_URL`:

- `jdbc:postgresql://127.0.0.1:5432/snap_player`

Importante:

- Não comitar esse arquivo
- Permissão recomendada: leitura apenas para root (`chmod 600`)

---

## 11. Ajustar Nginx (reverse proxy)

No arquivo `deploy/ubuntu/nginx/snap-player-api.conf`, ajuste:

- `server_name` para seu domínio real

O template já aponta para:

- app em `127.0.0.1:8080`
- health check em `/actuator/health`

Antes de subir:

- validar configuração do Nginx

TLS (recomendado antes de expor em produção):

- usar Certbot/Let's Encrypt
- redirecionar HTTP -> HTTPS

---

## 12. Subir a aplicação via systemd

Passos:

1. Recarregar unit files do `systemd`
2. Habilitar e iniciar `snap-player-api`
3. Testar e recarregar Nginx

O serviço usa:

- `--spring.profiles.active=prod`

Isso ativa:

- PostgreSQL via env (`DB_*`)
- S3 obrigatório
- storage local desabilitado
- validações fail-fast de produção

---

## 13. Validar o deploy (smoke checklist)

Valide localmente na VM:

- `http://127.0.0.1:8080/actuator/health`

Valide externamente:

- `https://SEU_DOMINIO/actuator/health`

Checklist:

- Serviço `snap-player-api` ativo
- Nginx ativo
- `/actuator/health` retornando `UP`
- Sem erro de credenciais S3
- Sem erro de conexão com PostgreSQL
- Flyway executado na inicialização

---

## 14. Operação diária (na unha)

Comandos úteis (manutenção):

- status/restart da app via `systemctl`
- logs da app via `journalctl -u snap-player-api`
- logs do Nginx em `/var/log/nginx/`
- reload do Nginx após mudanças de proxy/TLS

Observação:

- O worker assíncrono roda dentro da própria aplicação (mesmo processo).

---

## 15. Atualizar versão (deploy manual)

Fluxo simples:

1. Gerar novo `.jar`
2. Copiar para a VM (substituir `/opt/snap-player-api/snap-player-api.jar`)
3. Reiniciar serviço `snap-player-api`
4. Validar `/actuator/health`
5. Verificar logs

Boas práticas:

- Fazer backup do `.jar` anterior antes de substituir
- Fazer deploy em janela curta
- Validar um `POST /v2/snaps` e `GET /v2/snaps/{id}` após subir

---

## 16. Backups e persistência (mínimo recomendado)

Persistência principal:

- PostgreSQL (dados de domínio)
- Linode Object Storage (frames/snapshots)

Recomendado:

- Backup/rotina do PostgreSQL (dump)
- Política de retenção de backups
- Monitoramento de espaço em disco da VM (`/data/tmp`, logs)

Obs.:

- `/data/tmp/video-frames-processing` é temporário (limpo pela app), mas ainda precisa de espaço livre.

---

## 17. Troubleshooting rápido

### App não sobe

Verifique:

- `journalctl -u snap-player-api -n 200`
- `DB_*` corretos
- `STORAGE_*` corretos
- `ffmpeg/ffprobe` instalados
- permissões em `/data/tmp/video-frames-processing`

### Erro de health check no Nginx

Verifique:

- app ouvindo em `127.0.0.1:8080`
- `server_name` correto
- config Nginx válida
- firewall liberando `80/443`

### Erro de upload S3

Verifique:

- endpoint/região/bucket
- chaves de acesso
- `STORAGE_PUBLIC_BASE_URL`
- conectividade da VM para a internet

---

## 18. Próximo passo natural (futuro)

Quando quiser reduzir trabalho manual:

- CI gerando `.jar`
- script de deploy (rsync + restart + health check)
- depois, opcionalmente, trilha Docker/Compose

