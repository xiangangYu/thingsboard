///
/// Copyright © 2016-2024 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import {
  Compiler,
  Component,
  ComponentFactory,
  Injectable,
  Injector,
  NgModule,
  NgModuleRef,
  OnDestroy,
  Type,
  ɵresetCompiledComponents
} from '@angular/core';
import { from, Observable } from 'rxjs';
import { CommonModule } from '@angular/common';
import { catchError, map, mergeMap } from 'rxjs/operators';

@NgModule()
export abstract class DynamicComponentModule implements OnDestroy {

  // eslint-disable-next-line @angular-eslint/contextual-lifecycle
  ngOnDestroy(): void {
  }

}

interface DynamicComponentData<T> {
  componentType: Type<T>;
  componentModuleRef: NgModuleRef<DynamicComponentModule>;
}

interface DynamicComponentModuleData {
  moduleRef: NgModuleRef<DynamicComponentModule>;
  moduleType: Type<DynamicComponentModule>;
}

@Injectable({
    providedIn: 'root'
})
export class DynamicComponentFactoryService {

  private dynamicComponentModulesMap = new Map<Type<any>, DynamicComponentModuleData>();

  constructor(private compiler: Compiler,
              private injector: Injector) {
  }

  public createDynamicComponent<T>(
                     componentType: Type<T>,
                     template: string,
                     modules?: Type<any>[],
                     preserveWhitespaces?: boolean,
                     compileAttempt = 1,
                     styles?: string[]): Observable<DynamicComponentData<T>> {
    return from(import('@angular/compiler')).pipe(
      mergeMap(() => {
        const comp = this._createDynamicComponent(componentType, template, preserveWhitespaces, styles);
        let moduleImports: Type<any>[] = [CommonModule];
        if (modules) {
          moduleImports = [...moduleImports, ...modules];
        }
        // noinspection AngularInvalidImportedOrDeclaredSymbol
        const dynamicComponentInstanceModule = NgModule({
          declarations: [comp],
          imports: moduleImports
        })(class DynamicComponentInstanceModule extends DynamicComponentModule {});
        return from(this.compiler.compileModuleAsync(dynamicComponentInstanceModule)).pipe(
          map((module) => {
            let moduleRef: NgModuleRef<any>;
            try {
              moduleRef = module.create(this.injector);
            } catch (e) {
              this.compiler.clearCacheFor(module.moduleType);
              throw e;
            }
            this.dynamicComponentModulesMap.set(comp, {
              moduleRef,
              moduleType: module.moduleType
            });
            return {
              componentType: comp,
              componentModuleRef: moduleRef
            };
          }),
          catchError((error) => {
            if (compileAttempt === 1) {
              ɵresetCompiledComponents();
              return this.createDynamicComponent(componentType, template, modules, preserveWhitespaces, ++compileAttempt, styles);
            } else {
              throw error;
            }
          })
        );
      })
    );
  }

  public destroyDynamicComponent<T>(componentType: Type<T>) {
    const moduleData = this.dynamicComponentModulesMap.get(componentType);
    if (moduleData) {
      moduleData.moduleRef.destroy();
      this.compiler.clearCacheFor(moduleData.moduleType);
      this.dynamicComponentModulesMap.delete(componentType);
    }
  }

  private _createDynamicComponent<T>(componentType: Type<T>, template: string, preserveWhitespaces?: boolean, styles?: string[]): Type<T> {
    // noinspection AngularMissingOrInvalidDeclarationInModule
    return Component({
      template,
      preserveWhitespaces,
      styles
    })(componentType);
  }

}
