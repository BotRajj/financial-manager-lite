# Financial Manager Lite

A personal finance web application built with Java and Spring Boot. Manage bank accounts, credit cards, recurring expenses, installment plans, and monthly budgets — all in one place.

![demo](https://github.com/user-attachments/assets/3a7961f6-773e-44ef-a8e3-15e39c8e9eb8)

## Live Demo

**[financial-manager-lite-production.up.railway.app](https://financial-manager-lite-production.up.railway.app)**

Login with the demo account: `demo@demo.com` / `demo123`

## Features

- **Dashboard** — monthly income vs. expenses summary and upcoming payments
- **Bank accounts** — track balances across multiple accounts
- **Credit cards** — invoice tracking, installment plans with remaining totals and available limit
- **Transactions** — simple, recurring (monthly/annual), and installment entries
- **Budgets** — spending limits per category with real-time usage tracking
- **Spending projections** — cash flow simulation for future months
- **Debt management** — payables and receivables with installment schedules

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.3.5 |
| Security | Spring Security (form login, remember-me) |
| Persistence | Spring Data JPA, PostgreSQL 16, Flyway |
| Frontend | Thymeleaf, HTML/CSS/JS |
| Infra | Docker, Docker Compose |
| Deploy | Railway |

## Running locally

**Prerequisites:** Docker and Docker Compose installed.

```bash
git clone https://github.com/BotRajj/financial-manager-lite.git
cd financial-manager-lite
docker-compose up --build
```

Open [http://localhost:8080](http://localhost:8080) and log in with `demo@demo.com` / `demo123`.

The first build takes 3–5 minutes (Maven downloads dependencies inside the container). Subsequent starts are instant.

> To reset the database: `docker-compose down -v && docker-compose up --build`

## Roadmap

- [ ] UI/UX redesign — improved layout, responsiveness and visual consistency
- [ ] Budget module — complete category management and period comparison
- [ ] Notifications — WhatsApp and email alerts for upcoming bills and budget limits
- [ ] Multi-user — shared accounts between family members or partners
- [ ] Open Finance integration — automatic transaction import via Brazilian Open Finance APIs
