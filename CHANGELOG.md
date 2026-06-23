# Changelog

## [1.4.1](https://github.com/axel-nyman/balance-backend/compare/v1.4.0...v1.4.1) (2026-06-22)


### Documentation

* add product workflow (backlog, state doc, routine operating manual) ([9d3ea4e](https://github.com/axel-nyman/balance-backend/commit/9d3ea4ee59dab4f5c82083be60bfb0ad6ba6607d))
* add verified environment bootstrap to routine operating manual ([21a7767](https://github.com/axel-nyman/balance-backend/commit/21a7767d0069e57bd54f7de270f3985d4c393d24))
* mark item 010 (due-recurring hint) done and update STATE ([506ba8b](https://github.com/axel-nyman/balance-backend/commit/506ba8b7e876c88747a09150ea4bf0a3b6d67dc1))
* mark item 030 (wizard modal safe-area) done and update STATE ([326b13e](https://github.com/axel-nyman/balance-backend/commit/326b13eb5d33ef3eb2f6f08deb59707987610255))
* mark item 040 (collapse non-due recurring in wizard) done and update STATE ([1b9e6d1](https://github.com/axel-nyman/balance-backend/commit/1b9e6d18c55f81b04e520f8fb986a8a28028cf6f))
* narrow item 020 to PR-only preview image per review ([4052e1e](https://github.com/axel-nyman/balance-backend/commit/4052e1e160830b65b97f719feed42e310b1701cf))
* record review-driven correction for item 030 completion notes ([a24881d](https://github.com/axel-nyman/balance-backend/commit/a24881d2fb5fbf55ba74b4f60edeedc2eb5ab50d))
* revise item 015 backlog specs per maintainer review ([39f1b51](https://github.com/axel-nyman/balance-backend/commit/39f1b513fdcca7ee09aadd567fec686ed44f9ecf))
* rewrite backend README to describe Balance ([3110321](https://github.com/axel-nyman/balance-backend/commit/3110321d39b9decd56c1f1aa2727518afc24d709))
* rewrite backend README to describe Balance ([943c022](https://github.com/axel-nyman/balance-backend/commit/943c022e550eaf63b1b4dce2174d9a5330d79620))
* scope new-feature ideas into backlog specs (item 015) ([a4aba3c](https://github.com/axel-nyman/balance-backend/commit/a4aba3c55348f7f4d26c32ce34c7f782c28fa661))
* Update 015-scope-new-features.md ([a28b0d3](https://github.com/axel-nyman/balance-backend/commit/a28b0d3ccb6a4140080ba52c0ec73d8e8b44a5d5))

## [1.4.0](https://github.com/axel-nyman/balance-backend/compare/v1.3.0...v1.4.0) (2026-02-15)


### Features

* replace timestamp-based due dates with month/year calculation ([dd04c10](https://github.com/axel-nyman/balance-backend/commit/dd04c10b8a14b75c57f628527ca1d235be3b1575))


### Documentation

* **claude:** add month-based due date plan, research, and cleanup notes ([f935164](https://github.com/axel-nyman/balance-backend/commit/f935164060145e4e652952061e51ecbb3e9a55bd))

## [1.3.0](https://github.com/axel-nyman/balance-backend/compare/v1.2.1...v1.3.0) (2026-02-09)


### Features

* add optional bank account linking to recurring expenses ([538807c](https://github.com/axel-nyman/balance-backend/commit/538807cded40c0d386017400fb03b951d9a0aba7))


### Documentation

* **claude:** add research/planning docs ([aff107e](https://github.com/axel-nyman/balance-backend/commit/aff107e7e21c07e745a0f08e8481eb84d051fbd2))

## [1.2.1](https://github.com/axel-nyman/balance-backend/compare/v1.2.0...v1.2.1) (2026-01-09)


### Documentation

* add Docker infrastructure research and multi-platform build plan ([3e5313c](https://github.com/axel-nyman/balance-backend/commit/3e5313cc74835b9d7e2ff4389d3468576875d800))

## [1.2.0](https://github.com/axel-nyman/balance-backend/compare/v1.1.0...v1.2.0) (2026-01-09)


### Features

* implement Flyway database migrations ([e110c1b](https://github.com/axel-nyman/balance-backend/commit/e110c1bb48b8f3040a100b2a746c278bd4c22795))


### Documentation

* add Docker Compose deployment research notes ([90274b9](https://github.com/axel-nyman/balance-backend/commit/90274b9f76a98455cca0f1e7d411ec5b90248dec))
* add research and planning notes for release-please implementation ([f09f6d7](https://github.com/axel-nyman/balance-backend/commit/f09f6d7f85a8de845699a906aae55b7bd1eaf5ea))

## [1.1.0](https://github.com/axel-nyman/balance-backend/compare/v1.0.0...v1.1.0) (2026-01-09)


### Features

* add Claude Code agents and custom commands ([95bb7a9](https://github.com/axel-nyman/balance-backend/commit/95bb7a903d035c1af358902770d12b27fb753787))
* add delete functionality for budget expenses with validation ([d8e95f3](https://github.com/axel-nyman/balance-backend/commit/d8e95f399cad517676c21e30a8a4f4d605626f1e))
* add delete functionality for budget savings with validation and exception handling ([ff2a324](https://github.com/axel-nyman/balance-backend/commit/ff2a3241d840a9de01d944accab2bc284bc01e58))
* add functionality to update todo item status with validation for budget locking ([f619b3b](https://github.com/axel-nyman/balance-backend/commit/f619b3b9e8de866482469da5306a70bb34627699))
* Add todos to repo ([cb14196](https://github.com/axel-nyman/balance-backend/commit/cb1419616eb61d9eaaad4c2c8bad7384463c5a02))
* add update functionality for budget expenses ([ef4ad24](https://github.com/axel-nyman/balance-backend/commit/ef4ad2493c4650f25b6d5a86c5a8a81a6f151fc5))
* add update functionality for budget savings with validation and exception handling ([0c8a9ac](https://github.com/axel-nyman/balance-backend/commit/0c8a9ac6239baee0205def889f59f92364b2fdbe))
* add validation to prevent backdated balance history entries ([688690a](https://github.com/axel-nyman/balance-backend/commit/688690a452818d38448de96c2f4ff126167b4f01))
* added functionality to unlock a locked budget ([2b83982](https://github.com/axel-nyman/balance-backend/commit/2b83982d4565e8a9b00756e073f7636688ceec7c))
* allow bank account name reuse after soft delete ([72022e9](https://github.com/axel-nyman/balance-backend/commit/72022e9221aba8e8e54e58bc9a0d909620016b29))
* implement balance history retrieval with pagination and sorting ([af9899e](https://github.com/axel-nyman/balance-backend/commit/af9899eecac510836e6f53c1b664e01b47271518))
* implement balance update on budget lock with savings distribution ([9835423](https://github.com/axel-nyman/balance-backend/commit/9835423e6bf6f6cdb9f079abfbfa61e161a63945))
* implement budget deletion functionality with cascade delete for income, expenses, and savings ([d843a0a](https://github.com/axel-nyman/balance-backend/commit/d843a0a4a970236d702e5d5e82a411d431bdbe9e))
* implement budget detail view with income, expenses, and savings retrieval ([2af9468](https://github.com/axel-nyman/balance-backend/commit/2af946859707c9f4e205ec945fb1466467a81f50))
* implement budget locking functionality with validation for zero balance and update recurring expenses ([49d9b47](https://github.com/axel-nyman/balance-backend/commit/49d9b47d20f655a58130ed287bd55bb7769b2fd3))
* implement budget savings functionality with validation and exception handling ([11d91f5](https://github.com/axel-nyman/balance-backend/commit/11d91f5d4280d688124857ac93cefc384d6d685b))
* Implement Todo List functionality for Budget locking ([99a5269](https://github.com/axel-nyman/balance-backend/commit/99a5269e9823d699082dc2a055f21e6290f2cc7b))
* implement transfer calculation utility with greedy algorithm for optimal money transfers ([b392084](https://github.com/axel-nyman/balance-backend/commit/b3920841731dd0ed4416eda00237d276034eb14b))
* prevent backdated balance history entries ([a881c75](https://github.com/axel-nyman/balance-backend/commit/a881c7541608abced50a87c276567d99e1a94027))
* remove CLAUDE configuration files from .gitignore ([cc51a4e](https://github.com/axel-nyman/balance-backend/commit/cc51a4eec340a282d1c7a1455e8bd1fce770dcea))
* update Dockerfile for multi-stage build and add .dockerignore ([96ada11](https://github.com/axel-nyman/balance-backend/commit/96ada11918564fa1c01625a46743060ba4bb2819))


### Bug Fixes

* calculate correct budget totals in get all budgets endpoint ([3edcf91](https://github.com/axel-nyman/balance-backend/commit/3edcf9122e2182dfc9984c196b4e984f07799d21))
* enhance timestamp matcher to allow microsecond tolerance ([1b5e2db](https://github.com/axel-nyman/balance-backend/commit/1b5e2db4178e1b8ef650ae91e04f2b648f2cd6a4))
* store user-provided date in balance history changeDate field ([cfa9494](https://github.com/axel-nyman/balance-backend/commit/cfa9494b06aaa2d2a102e5ce6d05b5bc21b7dff5))


### Documentation

* add research and plan for budget totals fix ([6708191](https://github.com/axel-nyman/balance-backend/commit/67081917df23a844b23bf4e962593a5d1f9258df))
