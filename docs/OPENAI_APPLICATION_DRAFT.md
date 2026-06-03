# OpenAI Codex for Open Source 申请草稿

## Repository

https://github.com/hailiangziyuan/take-me-home

## Project name

Take Me Home / 带我回家

## Maintainer role

I am the primary maintainer and original creator of this Android project. I designed the product direction, implemented the MVP, maintain the codebase, and review safety/privacy boundaries for future contributions.

## Project summary

Take Me Home is a local-first Android self-practice app for gentle mental-health literacy and emotional self-regulation exercises. It provides a 25-minute guided routine with stages for grounding, mindful observation, self-compassion, cognitive reframing, and a small daily commitment.

The app intentionally avoids accounts, servers, cloud sync, analytics, or hidden data upload. Practice records stay on the user's device through Room, while settings stay in DataStore. It is not positioned as therapy, diagnosis, medical advice, or crisis response.

## Why this matters

Many people need a private, low-cost, non-stigmatizing way to practice emotional regulation and self-reflection between professional support sessions or before they are ready to seek help. Existing meditation apps are often generic, cloud-based, subscription-first, or not transparent about privacy.

This project explores a safer open-source pattern: local-first, privacy-preserving, evidence-aligned self-practice tools with explicit clinical and crisis boundaries.

## Current activity

The initial MVP includes:

- Kotlin + Jetpack Compose Android app
- local TextToSpeech guidance
- 25-minute staged practice flow
- optional local audio hooks
- Room-based local practice history
- DataStore-based local settings
- safety notice on first use
- local record deletion
- README, privacy, security, and contribution guidelines

## How Codex and API credits would help

Codex would help maintain this project by:

- reviewing pull requests for privacy regressions, hidden network calls, and unsafe clinical claims
- improving Android accessibility and localization
- adding tests and build checks
- generating safer, more generic practice templates from strict safety guidelines
- maintaining contributor docs and release notes
- helping audit code before public releases

API credits would support:

- optional maintainer-side review tools for script safety and tone
- automated PR summaries
- contributor onboarding helpers
- documentation translation for non-English users

## Safety and privacy commitments

The project will keep the public core:

- local-first
- no default networking
- no diagnosis or treatment claims
- no crisis-chatbot behavior
- no hidden data upload
- no private user stories in public scripts
- no unlicensed audio or media assets

Future AI-assisted personalization, if added, should require explicit user consent, clear privacy controls, and professional review for high-risk use cases.
