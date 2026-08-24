# ScanVAG v1.0 — READ ONLY

Aplicativo Android experimental para leitura VAG/UDS por adaptador ELM327 Bluetooth Classic (SPP).

## Estado desta versão

Esta v1.0 foi deliberadamente construída em **modo somente leitura**. O cliente ELM tem uma whitelist que aceita apenas:

- comandos `AT...` do adaptador ELM327;
- UDS `22` — ReadDataByIdentifier;
- UDS `19` — ReadDTCInformation.

Comandos de escrita como `2E`, `27`, `31`, `11`, `14` etc. não são aceitos pelo cliente nesta versão.

## Funções

- seleção de dispositivo Bluetooth pareado;
- conexão SPP com ELM327;
- identificação do ELM (`ATI`);
- leitura do protocolo (`ATDP`);
- leitura VIN via `22 F190`;
- Scan VAG dos módulos validados no veículo de teste;
- leitura `F191`, `F187` e coding `0600`;
- montagem de respostas ISO-TP multi-frame;
- leitura UDS DTC via `19 02 FF`;
- backup local em TXT;
- compartilhamento do relatório pelo Android.

## Módulos atualmente configurados

| Endereço | Módulo | TX | RX |
|---|---|---|---|
| 01 | Motor | 7E0 | 7E8 |
| 02 | Câmbio | 7E1 | 7E9 |
| 09 | BCM / Central Elétrica | 70E | 778 |
| 17 | Painel / Instruments | 714 | 77E |
| 19 | Gateway | 710 | 77A |
| 5F | Multimídia | 773 | 7DD |

## Compilar

### Android Studio / ambiente Gradle

Abra a pasta raiz do projeto e execute:

```bash
./gradlew assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### GitHub Actions

O projeto inclui `.github/workflows/build-apk.yml`.

1. envie esta pasta para um repositório GitHub;
2. abra **Actions**;
3. execute **Build ScanVAG APK**;
4. baixe o artefato `ScanVAG-v1.0-debug-apk`.

## Primeiro teste recomendado

1. pareie o ELM327 no Android;
2. feche outros apps que possam estar usando o ELM;
3. ligue a ignição;
4. abra ScanVAG;
5. conceda permissão de dispositivos próximos;
6. selecione OBDII/ELM327;
7. toque em **Conectar**;
8. confirme VIN/ELM/protocolo;
9. toque em **SCAN VAG**;
10. salve o backup antes de qualquer evolução futura para escrita.

## Observação

O ScanVAG v1.0 ainda é uma ferramenta experimental. A camada de escrita/coding não está implementada nesta versão.
