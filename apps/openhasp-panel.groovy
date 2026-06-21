/*
 * Copyright 2026 NichUK
 *
 * Licensed under the Apache License, Version 2.0.
 */

definition(
    name: 'OpenHASP Panel',
    namespace: 'nichuk',
    author: 'NichUK',
    description: 'Deprecated compatibility app. OpenHASP plates are now configured directly in OpenHASP Manager.',
    category: 'Convenience',
    parent: 'nichuk:OpenHASP Manager',
    importUrl: 'https://raw.githubusercontent.com/NichUK/Hubitat-OpenHASP/main/apps/openhasp-panel.groovy',
    iconUrl: '',
    iconX2Url: '',
    singleInstance: false
)

preferences {
    page(name: 'mainPage')
}

def mainPage() {
    dynamicPage(name: 'mainPage', title: 'OpenHASP Panel - Deprecated', install: false, uninstall: true) {
        section('Deprecated') {
            paragraph 'OpenHASP Panel child apps were replaced in version 0.4.0. Configure plates directly in the OpenHASP Manager app instead.'
        }
    }
}

void installed() {}
void updated() {}
void uninstalled() {}
