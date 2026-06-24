import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ConfigModule, ConfigService } from '@nestjs/config';
import * as http from 'http';
import { AddressInfo } from 'net';
import nock from 'nock';
import {
  PostgreSqlContainer,
  StartedPostgreSqlContainer,
} from '@testcontainers/postgresql';
import { DataSource, Repository } from 'typeorm';
import { AppController } from '../../src/app.controller';
import { AppService } from '../../src/app.service';
import { HealthController } from '../../src/api/controller/health.controller';
import { OrderController } from '../../src/api/controller/order.controller';
import { CouponController } from '../../src/api/controller/coupon.controller';
import { OrderService } from '../../src/core/services/order.service';
import { CouponService } from '../../src/core/services/coupon.service';
import { ErpGateway } from '../../src/core/services/external/erp.gateway';
import { ClockGateway } from '../../src/core/services/external/clock.gateway';
import { TaxGateway } from '../../src/core/services/external/tax.gateway';
import { GlobalExceptionFilter } from '../../src/api/exception/global-exception.filter';
import { CustomValidationPipe } from '../../src/api/exception/custom-validation.pipe';
import { DecimalFormatInterceptor } from '../../src/api/interceptor/decimal-format.interceptor';
import { Order } from '../../src/core/entities/order.entity';
import { Coupon } from '../../src/core/entities/coupon.entity';

export const ERP_URL = 'http://erp-stub.local';
export const TAX_URL = 'http://tax-stub.local';
export const CLOCK_URL = 'http://clock-stub.local';

/**
 * In-process component-test harness: boots the Nest app on a random port (real HTTP over a real
 * socket), backs it with a Testcontainers-managed Postgres (real dialect/numeric semantics) and
 * stubs the ERP / Tax / Clock external HTTP systems in-process with nock. No docker compose, no
 * deployment. Mirrors the Java `AbstractComponentTest` and is shared by both the Component suite
 * and the Pact provider-verification test.
 *
 * Start the container BEFORE activating nock: nock patches Node's HTTP client and, once
 * enableNetConnect is restricted to 127.0.0.1, blocks Testcontainers' communication with the Docker
 * daemon socket (host 'localhost', not 127.0.0.1). That makes runtime detection fail with "Could
 * not find a working container runtime strategy". The Postgres connection itself is raw TCP via
 * node-postgres, which nock does not intercept, so the DB keeps working afterwards.
 */
export class ComponentHarness {
  app!: INestApplication;
  port!: number;
  dataSource!: DataSource;
  orderRepo!: Repository<Order>;
  couponRepo!: Repository<Coupon>;
  private postgres!: StartedPostgreSqlContainer;

  async start(): Promise<void> {
    this.postgres = await new PostgreSqlContainer('postgres:16-alpine')
      .withDatabase('app')
      .withUsername('app')
      .withPassword('app')
      .start();

    nock.enableNetConnect('127.0.0.1');

    const moduleRef = await Test.createTestingModule({
      imports: [
        ConfigModule.forRoot({ isGlobal: true }),
        TypeOrmModule.forRoot({
          type: 'postgres',
          host: this.postgres.getHost(),
          port: this.postgres.getPort(),
          username: this.postgres.getUsername(),
          password: this.postgres.getPassword(),
          database: this.postgres.getDatabase(),
          entities: [Order, Coupon],
          synchronize: true,
        }),
        TypeOrmModule.forFeature([Order, Coupon]),
      ],
      controllers: [
        AppController,
        HealthController,
        OrderController,
        CouponController,
      ],
      providers: [
        AppService,
        OrderService,
        CouponService,
        ErpGateway,
        ClockGateway,
        TaxGateway,
        {
          provide: ConfigService,
          useValue: {
            get: (key: string, defaultValue?: unknown) => {
              const cfg: Record<string, string> = {
                ERP_API_URL: ERP_URL,
                TAX_API_URL: TAX_URL,
                CLOCK_API_URL: CLOCK_URL,
                EXTERNAL_SYSTEM_MODE: 'stub',
              };
              return cfg[key] ?? defaultValue;
            },
          },
        },
      ],
    }).compile();

    this.app = moduleRef.createNestApplication();
    this.app.useGlobalPipes(new CustomValidationPipe());
    this.app.useGlobalFilters(new GlobalExceptionFilter());
    this.app.useGlobalInterceptors(new DecimalFormatInterceptor());
    await this.app.listen(0);

    const server = this.app.getHttpServer() as http.Server;
    this.port = (server.address() as AddressInfo).port;

    this.dataSource = moduleRef.get(DataSource);
    this.orderRepo = this.dataSource.getRepository(Order);
    this.couponRepo = this.dataSource.getRepository(Coupon);
  }

  async stop(): Promise<void> {
    await this.app.close();
    nock.cleanAll();
    nock.restore();
    await this.postgres.stop();
  }

  /** Clears in-process external stubs and empties the DB — call before each test/provider state. */
  async resetState(): Promise<void> {
    nock.cleanAll();
    nock.enableNetConnect('127.0.0.1');
    await this.orderRepo.clear();
    await this.couponRepo.clear();
  }

  baseUrl(): string {
    return `http://127.0.0.1:${this.port}`;
  }

  httpServer(): http.Server {
    return this.app.getHttpServer() as http.Server;
  }

  // --- External-system stub helpers (shared by component tests and provider states) ---
  // Persisted so a single state/test can drive multiple requests to the same endpoint.

  stubClock(isoInstant: string): void {
    nock(CLOCK_URL).persist().get('/api/time').reply(200, { time: isoInstant });
  }

  stubProduct(sku: string, price: number): void {
    nock(ERP_URL)
      .persist()
      .get(`/api/products/${sku}`)
      .reply(200, { id: sku, price });
  }

  stubProductMissing(sku: string): void {
    nock(ERP_URL).persist().get(`/api/products/${sku}`).reply(404);
  }

  stubPromotion(active: boolean, discount: number): void {
    nock(ERP_URL)
      .persist()
      .get('/api/promotion')
      .reply(200, { promotionActive: active, discount });
  }

  stubTax(country: string, rate: number): void {
    nock(TAX_URL)
      .persist()
      .get(`/api/countries/${country}`)
      .reply(200, { id: country, countryName: country, taxRate: rate });
  }
}
