import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { Verifier } from '@pact-foundation/pact';
import * as path from 'path';
import * as http from 'http';
import { AddressInfo } from 'net';
import nock from 'nock';
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
import { OrderStatus } from '../../src/core/entities/order-status.enum';
import { DataSource, Repository } from 'typeorm';

const ERP_URL = 'http://erp-stub.local';
const TAX_URL = 'http://tax-stub.local';
const CLOCK_URL = 'http://clock-stub.local';

describe('Backend Pact Provider Verification', () => {
  let app: INestApplication;
  let port: number;
  let dataSource: DataSource;
  let orderRepo: Repository<Order>;
  let couponRepo: Repository<Coupon>;

  beforeAll(async () => {
    nock.enableNetConnect('127.0.0.1');

    const moduleRef = await Test.createTestingModule({
      imports: [
        ConfigModule.forRoot({ isGlobal: true }),
        TypeOrmModule.forRoot({
          type: 'sqlite',
          database: ':memory:',
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

    app = moduleRef.createNestApplication();
    app.useGlobalPipes(new CustomValidationPipe());
    app.useGlobalFilters(new GlobalExceptionFilter());
    app.useGlobalInterceptors(new DecimalFormatInterceptor());
    await app.listen(0);

    const server = app.getHttpServer() as http.Server;
    port = (server.address() as AddressInfo).port;

    dataSource = moduleRef.get(DataSource);
    orderRepo = dataSource.getRepository(Order);
    couponRepo = dataSource.getRepository(Coupon);
  });

  afterAll(async () => {
    await app.close();
    nock.cleanAll();
    nock.restore();
  });

  const resetState = async () => {
    nock.cleanAll();
    nock.enableNetConnect('127.0.0.1');
    await orderRepo.clear();
    await couponRepo.clear();
  };

  const sampleOrder = (): Partial<Order> => ({
    orderTimestamp: new Date('2026-03-10T12:00:00Z'),
    country: 'US',
    sku: 'BOOK-123',
    quantity: 2,
    unitPrice: 10.0,
    basePrice: 20.0,
    discountRate: 0,
    discountAmount: 0,
    subtotalPrice: 20.0,
    taxRate: 0.1,
    taxAmount: 2.0,
    totalPrice: 22.0,
    status: OrderStatus.PLACED,
    appliedCouponCode: null,
  });

  it('verifies the frontend consumer contract', async () => {
    const pactFile = path.resolve(
      __dirname,
      '../../../../contracts/frontend-backend.json',
    );

    await new Verifier({
      provider: 'backend',
      providerBaseUrl: `http://127.0.0.1:${port}`,
      pactUrls: [pactFile],
      stateHandlers: {
        'product BOOK-123 exists and US is taxable': async () => {
          await resetState();
          nock(CLOCK_URL)
            .get('/api/time')
            .reply(200, { time: '2026-03-10T12:00:00Z' });
          nock(ERP_URL)
            .get('/api/products/BOOK-123')
            .reply(200, { id: 'BOOK-123', price: 10.0 });
          nock(ERP_URL)
            .get('/api/promotion')
            .reply(200, { promotionActive: false, discount: 1.0 });
          nock(TAX_URL)
            .get('/api/countries/US')
            .reply(200, { id: 'US', countryName: 'US', taxRate: 0.1 });
        },

        'order placement is blocked by the New Year blackout': async () => {
          await resetState();
          nock(CLOCK_URL)
            .get('/api/time')
            .reply(200, { time: '2026-12-31T23:59:00Z' });
        },

        'at least one order exists': async () => {
          await resetState();
          await orderRepo.save(
            orderRepo.create({ ...sampleOrder(), orderNumber: 'ORD-HIST-1' }),
          );
        },

        'order ORD-1 exists': async () => {
          await resetState();
          await orderRepo.save(
            orderRepo.create({ ...sampleOrder(), orderNumber: 'ORD-1' }),
          );
        },

        'no order UNKNOWN exists': async () => {
          await resetState();
          // DB is empty after resetState.
        },

        'at least one coupon exists': async () => {
          await resetState();
          await couponRepo.save(
            couponRepo.create({
              code: 'SAVE10',
              discountRate: 0.2,
              usageLimit: 100,
              usedCount: 0,
              validFrom: null,
              validTo: null,
            }),
          );
        },

        'no coupon SAVE10 exists yet': async () => {
          await resetState();
          // DB is empty after resetState.
        },
      },
    }).verifyProvider();
  });
});
