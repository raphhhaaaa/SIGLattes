# 🎓 Extrator Lattes (CNPq) - UEM

![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Hibernate](https://img.shields.io/badge/Hibernate-59666C?style=for-the-badge&logo=hibernate&logoColor=white)
![IBM DB2](https://img.shields.io/badge/IBM%20DB2-052FAD?style=for-the-badge&logo=ibm&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![ZK Framework](https://img.shields.io/badge/ZK_Framework-0081CB?style=for-the-badge&logo=zk&logoColor=white)
![Status](https://img.shields.io/badge/Status-Em_Desenvolvimento-yellow?style=for-the-badge)

> Uma solução robusta para extração, processamento e análise de Currículos Lattes através do WebService institucional do CNPq, desenvolvida no contexto da **Universidade Estadual de Maringá (UEM)**.

---

## Considerações Iniciais Importantes ## 

1. Este projeto está sendo desenvolvido em contexto interno da Universidade Estadual de Maringá, e não é de domínio público. Suas respectivas licenças e
   direitos estão reservados À Pró-Reitoria de Planejamento e Desenvolvimento Institucional (PLD) - Divisão de Informações e Planos (LNI). Qualquer uso indevido
   ou não autorizado de todo e qualquer código ou informação armazenada nesse repositório, poderá acarretar em medidas administrativas cabíveis.

2. Por causa de cláusulas contratuais, o acesso ao WebService do CNPq pela UEM é restrito a apenas um IP específico da instituição. Dado que, no momento, não possuo
   acesso a esse IP, optei por utilizar uma técnica chamada de Tunelamento por SSH, que consiste em basicamente redirecionar o fluxo de requisições, criando uma porta local
   na máquina de desenvolvimento, encapsulando todo tráfego enviado para o localhost:8888 e entregando diretamente ao servidor do CNPq na porta 80.

   Por esse motivo:

   [🛜 Configuração de rede (Tunel via SSH)](#-infraestrutura-de-rede-tunel-via-ssh)

4. Em ambiente de desenvolvimento, este projeto usa containerização. O banco de dados DB2 utilizado é acessado através de uma imagem pública do Docker.

   Informações adicionais em:

   [🚀 Guia de Configuração Docker DB2 (dev)](#-guia-de-configuracao-docker-db2-dev)

---


## 📖 Sobre o Projeto

Este sistema automatiza a recuperação de dados acadêmicos da Plataforma Lattes. Ele atua como uma ponte entre o repositório governamental (CNPq) e o banco de dados da UEM, permitindo:

1.  **Conexão Segura:** Acesso ao serviço SOAP `WSCurriculo` via HTTP (ou Tunelamento SSH para dev).
2.  **Processamento XML:** Parsing avançado de currículos (Dados Pessoais, Formação, Produção Bibliográfica).
3.  **Persistência Relacional:** Armazenamento estruturado (1:N) para análise de dados.
4.  **Visualização:** Interface Web interativa para consulta, detalhamento e extração de novos currículos.

---

## ⚠️ Limitação Institucional (Crítico)



> **Nota:** Para testes e homologação, utilize estritamente IDs Lattes de docentes ou discentes vinculados à UEM.

---

## 🛠️ Tech Stack & Arquitetura

O projeto segue uma arquitetura MVVM (Model-View-ViewModel) utilizando:

* **Backend:** Java 11 (Lógica de Negócio e Parsers DOM).
* **ORM:** Hibernate / JPA (Mapeamento Objeto-Relacional).
* **Frontend:** ZK Framework (ZUL + ViewModels).
* **Database:** DB2 com Docker (ambiente de desenvolvimento). IBM DB2 para produção.
* **Infraestrutura:** Apache Tomcat 9 + SSH Port Forwarding (ambiente de desenvolvimento).

---

## 🚀 Guia de Configuracao Docker DB2 dev

### 1. Banco de Dados (Docker)
Utilizamos uma imagem oficial da IBM. O volume `lattes_db_data` garante que os dados persistam mesmo após a remoção do contentor.

```bash
# Criar e iniciar o motor do banco
docker run -itd \
  --name db2_server \
  --hostname db2_server \
  --privileged=true \
  -p 50000:50000 \
  -e LICENSE=accept \
  -e DB2INST1_PASSWORD=sua_senha \
  -v lattes_db_data:/database \
  icr.io/db2_community/db2:12.1.4.0
```

### 2. Instalação de dependências
1. Instale o Maven no seu sistema caso ainda não o tenha. 
2. Rode o seguinte comando no cmd de sua IDE para instalar as dependências do projeto e construir a build executável:
```bash 
mvn clean install && mvn clean package
```

---

## 🛜 Infraestrutura de rede Tunel via SSH

Antes de iniciar a aplicação, é obrigatório estabelecer o túnel para "enganar" o Java e acessar o CNPq como se fosse local.

```bash
# Execute no terminal e mantenha ABERTO.
ssh -N -L 8888:servicosweb.cnpq.br:80 pld@186.233.154.49 -p 9122 
```
---

## ⚙️ Configuração ##

No arquivo ```src/main/resources/hibernate.cfg.xml```, configure suas credenciais de banco:

```xml
<property name="connection.url">jdbc:db2://localhost:50000/LATTES</property>
<property name="hibernate.dialect">org.hibernate.dialect.DB2Dialect</property>
<property name="connection.username">db2inst1</property>
<property name="connection.password">sua_senha</property>
```

> **Nota:** Atenção ao configurar bancos de PRODUÇÃO.

---

## ▶️ Execução (local / dev)

1. Realize o Build do artefato (.war / .jar).

2. Faça o deploy no Tomcat 9.

3. Acesse: ```http://localhost:8080/ExtratorLattes/```

---

## ⚒️ Extrator Lattes

### 1. Login:
Ao entrar no sistema pela primeira vez, o usuário se deparará com a tela de login, e os campos de Usuário e Senha, respectivamente. 
Para ter acesso, existem dois caminhos:

1. Concedido pelo admin: O administrador do sistema ativamente cria um usuário para a pessoa que irá utilizar o sistema, e com as credenciais, essa pessoa poderá acessar a aplicação.

2. Servidor LDAP UEM: Através da autenticação pelo servidor LDAP da UEM o usuário poderá colocar suas credenciais instituicionais, que serão validadas e, caso autorizado, liberadadas
para acesso, com uma conta temporária.

<img width="2566" height="1319" alt="Captura de tela de 2026-05-07 09-16-23" src="https://github.com/user-attachments/assets/ebba6add-d4fe-47e0-a327-d285a871dfff" />

> **Nota 1:** A autenticação por LDAP UEM se restringe apenas à docentes/funcionários vinculados à UEM. Alunos, por padrão, são impedidos de entrar no sistema.

> **Nota 2:** Existem dois níveis de acesso: **Administrador** e **Usuário Comum**. O administrador possui acesso integral a todas as funcionalidades do sistema, incluindo configurações. Já o 
> Usuário Comum possui acesso às principais funcionalidades de análise de dados (como acessar os relátorios e cadastros) além de poder extrair novos currículos. (** passível de  mudança futura **)

### 2. Página Inicial (index)
A página inicial é o coração do sistema. Assim que o usuário entra, ele pode ter acesso à:

 - O Extrator de Currículos Lattes (Por ID Lattes, Por CPF, Extração em Lote, Upload de XML (apenas admin) );
 - Gráficos de Produção Científica da UEM;
 - Dashboard das métricas do sistema (Status de conexão com o CNPq, quantidade de currículos cadastrados no banco, quantidade
 - de currículos desatualizados no momento e currículos processados no dia.);
 - Menu Lateral.

<img width="2566" height="1319" alt="Captura de tela de 2026-05-07 09-17-01" src="https://github.com/user-attachments/assets/976bc652-c78f-4843-9f96-230f8ba94eab" />

### 3. Verificar Atualizações
Nesta página o usuário pode buscar por currículos desatualizados e atualiza-los. De forma individual ou geral.

<img width="2566" height="1319" alt="Captura de tela de 2026-05-07 09-15-58" src="https://github.com/user-attachments/assets/944f1296-939b-4171-9bdc-9ecf081dc26d" />

---

# **_Guia de Páginas_**

## CADASTROS:

### 👤 _Pessoas_
Tabela que guarda o registro de todos os currículos cadastrados no banco de dados do sistema. Através dela é possível clicar
no botão de ação "Ver detalhes" que abrirá um modal detalhado com uma versão compactada do currículo Lattes daquela pessoa.

<img width="2566" height="1319" alt="Captura de tela de 2026-05-07 09-19-04" src="https://github.com/user-attachments/assets/c089d0fe-663d-47de-bd18-9772a97b7a68" />

### 🏫 _Instituições_
Tabela que guarda o registro de todas as instituições colhidas na extração dos currículos. Exibe também o número de ocorrências
que cada instituição registrou ao total.

<img width="2564" height="1324" alt="Captura de tela de 2026-05-19 10-15-13" src="https://github.com/user-attachments/assets/75750426-0975-4be1-99f9-2ab6a5ff354b" />


### 📖 _Cursos_ 
Tabela que guarda o registro de todos os cursos colhidos na extração dos currículos.

<img width="2566" height="1319" alt="Captura de tela de 2026-05-07 09-21-21" src="https://github.com/user-attachments/assets/ae2209f1-920f-4db9-b94d-4829e40a554a" />

## RELATÓRIOS:

### 📊 _Relatório Dinâmico_
Ferramenta de busca avançada que permite cruzar filtros (como tipo de produção, instituição vinculada) sobre os dados
pessoais e de formação dos currículos. Através dela, a gestão ganha total flexibilidade para mapear a produtividade da instituição sob
demanda, facilitando a formação de comitês e o atendimento a editais de fomento.

<img width="2566" height="1319" alt="Captura de tela de 2026-05-07 09-25-12" src="https://github.com/user-attachments/assets/bf5299a4-7890-49a4-886a-28976b63c97b" />

### 🌐 _Relatório de Revistas_
Lista que agrupa e contabiliza os veículos de publicação científica extraídos da produção bibliográfica dos pesquisadores.
Exibe o nome das revistas, o ISSN, a nota Qualis CAPES e o volume de publicações registradas no banco de dados, permitindo ranquear e mensurar o
fator de impacto, qualidade e o alcance da ciência produzida. É possível filtrar por nome ou nota CAPES (A1, A2, B3, C...).

<img width="2566" height="1319" alt="Captura de tela de 2026-05-07 09-25-40" src="https://github.com/user-attachments/assets/a26c8ddc-95cb-4b6e-8681-398c94121ef7" />

### 🏆 _Relatório de Produtividade_
Painel que quantifica as entregas acadêmicas e o impacto bibliométrico (artigos, índice H, n° de citações) geradas por pesquisadores em uma tabela
de classificação geral. Gera também um pódio com o Top 3 pesquisadores da instituição selecionada. Funciona como o principal
termômetro de desempenho quantitativo da universidade, gerando
os dados vitais para embasar a distribuição de bolsas e progressões de carreira.

<img width="2566" height="1319" alt="Captura de tela de 2026-05-07 09-26-19" src="https://github.com/user-attachments/assets/d977740f-db47-4598-ba42-ad4248062ea8" />

### 💻 Consulta SQL Livre (admin only)
Interface de terminal restrita exclusivamente a administradores que permite a execução direta de scripts SQL no banco de
dados (apenas comandos de LEITURA são permitidos). Acessa todas as tabelas e relacionamentos do esquema DB2 de forma bruta,
sem filtros pré-definidos da interface gráfica. Gera valor ao oferecer poder total para extrações emergenciais ou a criação
de relatórios complexos sob medida que ainda não possuem um módulo visual dedicado no sistema.

<img width="2566" height="1319" alt="Captura de tela de 2026-05-07 09-26-38" src="https://github.com/user-attachments/assets/7f926820-744c-4a05-8197-25ea41322822" />

## SISTEMA:

### 🧑🏻‍💻 _Usuários_
Gestão centralizada de contas e níveis de acesso (Administrador ou Utilizador Comum) do sistema. Permite a criação de novos
perfis e a edição de permissões, garantindo o controle rigoroso de quem pode interagir com as funcionalidades de extração e
outras.

<img width="2566" height="1319" alt="Captura de tela de 2026-05-07 09-27-12" src="https://github.com/user-attachments/assets/6a9864e9-09b2-4e34-b358-1426caec3daa" />

### ⚙️ _Configurações_
Painel de controle dinâmico que permite ajustar parâmetros operacionais do sistema em tempo real sem a necessidade de reiniciar
o servidor. Através desta tela, o administrador configura intervalos de backup, agendamentos de sincronização, credenciais
de e-mail e outras propriedades do `ConfigManager` que ditam o comportamento da automação. É possível também nessa tela alterar
o endpoint do webservice utilizado e fazer upload manual do arquivo .csv do Qualis CAPES.

<img width="2549" height="1229" alt="Captura de tela de 2026-05-26 08-58-01" src="https://github.com/user-attachments/assets/b5ee7376-3f96-4580-aeb6-8baf43672be7" />

### 📜 _Logs_
Repositório de eventos que regista toda a atividade técnica e operacional da plataforma. Exibe o histórico de execução das
tarefas de fundo, o status das extrações com o WebService do CNPq, histórico de acessos/tentativas de login e eventuais erros
operacionais, servindo como a ferramenta primária para auditoria e depuração de infraestrutura por parte dos administradores.

<img width="2459" height="641" alt="Captura de tela de 2026-05-07 09-28-57" src="https://github.com/user-attachments/assets/af489e34-d089-45d1-bbbb-9e8451fa76c4" />

---

## ⚙️ Funcionalidades de Automação (AutomacaoService)

O sistema possui um módulo autonômo (Background Task) que garante a estabilidade e a atualização dos dados sem necessidade de intervenção manual:

* **Backup:** Realiza cópias de segurança binárias nativas do Docker (`.001`) utilizando *Archive Logging*, sem derrubar as conexões dos utilizadores ou causar lentidão. Os backups ficam salvos de forma segura no volume persistente do Docker (/database/data).
* **Sincronização Agendada:** Varredura automática por todos os registros para identificar currículos desatualizados na base local e baixar versões mais recentes do repositório do CNPq.
* **Serviço SMTP (Envio de E-mails Automáticos):** Exige um E-mail do Sistema e um E-mail Administrador. Manda e-mails automáticos para o  admin a respeito de informações do sistema. Notifica quedas de conexão com o CNPq, sincronizações automáticas de curriculos desatualizados, relatórios semanais com métricas de produção, etc...

> **Nota:** Os intervalos de tempo e chaves de ativação destes serviços são controlados dinamicamente, permitindo ajustes de perfomance para o servidor de produção.

---
## 📝 Licença

Este projeto é de uso interno e acadêmico. Desenvolvido sob as diretrizes da Pró-reitoria de 
Planejamento e Desenvolvimento Institucional (PLD).
