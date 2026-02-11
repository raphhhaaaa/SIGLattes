# 🎓 Extrator Lattes (CNPq) - UEM

![Java](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Hibernate](https://img.shields.io/badge/Hibernate-59666C?style=for-the-badge&logo=hibernate&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-005C84?style=for-the-badge&logo=mysql&logoColor=white)
![ZK Framework](https://img.shields.io/badge/ZK_Framework-0081CB?style=for-the-badge&logo=zk&logoColor=white)
![Status](https://img.shields.io/badge/Status-Em_Andamento-yellow?style=for-the-badge)

> Uma solução robusta para extração, processamento e análise de Currículos Lattes através do WebService institucional do CNPq, desenvolvida no contexto da **Universidade Estadual de Maringá (UEM)**.

---

## 📖 Sobre o Projeto

Este sistema automatiza a recuperação de dados acadêmicos da Plataforma Lattes. Ele atua como uma ponte entre o repositório governamental (CNPq) e bancos de dados locais, permitindo:

1.  **Conexão Segura:** Acesso ao serviço SOAP `WSCurriculo` via Tunelamento SSH.
2.  **Processamento XML:** Parsing avançado de currículos (Dados Pessoais, Formação, Produção Bibliográfica).
3.  **Persistência Relacional:** Armazenamento estruturado (1:N) para análise de dados.
4.  **Visualização:** Interface Web interativa para consulta e detalhamento.

---

## ⚠️ Limitação Institucional (Crítico)

Devido às políticas de segurança do CNPq, este software opera sob restrições de IP institucional.

| Status | Tipo de Pesquisador | Comportamento do Sistema |
| :---: | :--- | :--- |
| ✅ | **Vínculo UEM** | Extração completa (XML baixado e processado). |
| ❌ | **Externo (USP, Unicamp...)** | Bloqueado pelo CNPq (Retorna "Dados Vazios"). |

> **Nota:** Para testes e homologação, utilize estritamente IDs Lattes de docentes ou discentes vinculados à UEM.

---

## 🛠️ Tech Stack & Arquitetura

O projeto segue uma arquitetura MVC (Model-View-ViewModel) utilizando:

* **Backend:** Java 11 (Lógica de Negócio e Parsers DOM).
* **ORM:** Hibernate / JPA (Mapeamento Objeto-Relacional).
* **Frontend:** ZK Framework (ZUL + ViewModels).
* **Database:** MySQL Server.
* **Infraestrutura:** Apache Tomcat 9 + SSH Port Forwarding.

---

## 🚀 Guia de Instalação

### 1. Banco de Dados (para testes locais)
Crie o schema no MySQL. O Hibernate encarrega-se de criar as tabelas automaticamente (`hbm2ddl.auto = update`).

```sql
CREATE DATABASE LATTESEXTRATOR;
```

### 2. Instalação de depêndencias
1. Instale o Maven no seu sistema caso não houver. 
2. Rode o seguinte comando no cmd de sua IDE para instalar as depêndencias do projeto e construir a build executável:
```bash 
mvn clean install && mvn clean package
```

---

## 🛜 Infraestrutura de rede (Túnel via SSH)

Antes de iniciar a aplicação, é obrigatório estabelecer o túnel para "enganar" o Java e acessar o CNPq como se fosse local.

```bash
# Execute no terminal e mantenha ABERTO.
ssh ssh -N -L 8888:servicosweb.cnpq.br:80 pld@186.233.154.49 -p 9122 
```
---

## ⚙️ Configuração ##

No arquivo ```src/main/resources/hibernate.cfg.xml```, configure suas credenciais de banco:

```xml
<property name="connection.url">jdbc:mysql://localhost:3306/LATTESEXTRATOR</property>
<property name="connection.username">root</property>
<property name="connection.password">sua_senha</property>
```

> **Nota:** Atenção ao configurar bancos de produção.

---

## ▶️ Execução (local)

1. Realize o Build do artefato (.war).

2. Faça o deploy no Tomcat 9.

3. Acesse: ```http://localhost:8080/ExtratorLattes```

---
## 📝 Licença

Este projeto é de uso interno e acadêmico. Desenvolvido sob as diretrizes da Pró-reitoria de 
Planejamento e Desenvolvimento Institucional (PLD).